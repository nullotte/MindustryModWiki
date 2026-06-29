package wikigen.util;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Http.*;
import arc.util.serialization.*;
import arc.util.serialization.Jval.*;
import mindustry.*;
import mindustry.io.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;

public class ModListUtils {
    public static final int minStars = 10;

    private static final Json json = new Json();
    private static final String[] metaFiles = {"mod.json", "mod.hjson", "plugin.json", "plugin.hjson"};

    public static ModListing currentModListing;
    public static Fi currentModFile;
    private static Seq<ModListing> modListings;

    public static Fi modDirectory() {
        return Config.mindustryDataDirectory.child("mods/");
    }

    public static void initMod(int index) {
        modDirectory().emptyDirectory();

        Seq<ModListing> filteredModListings = getFilteredModListings();
        if (filteredModListings == null || index < 0 || index >= filteredModListings.size) return;
        currentModListing = filteredModListings.get(index);
        githubImportMod(currentModListing.repo, currentModListing.hasJava, null);

        ZipFi modFile = new ZipFi(modDirectory().child(currentModListing.repo.replace("/", "") + ".zip"));
        ModMeta modMeta = null;

        try {
            modMeta = findMeta(resolveRoot(modFile));
        } catch (Exception ignored) {}

        if (modMeta != null && modMeta.dependencies.any()) {
            ModMeta finalModMeta = modMeta; // what?
            getModListings().each(modListing -> finalModMeta.dependencies.contains(modListing.internalName), modListing -> {
                githubImportMod(modListing.repo, modListing.hasJava, null);
            });
        }
    }

    public static Seq<ModListing> getFilteredModListings() {
        return getModListings().select(m -> m.stars >= minStars);
    }

    public static Seq<ModListing> getModListings() {
        if (modListings == null) {
            modListings = parseModListings();
        }
        return modListings;
    }

    private static Seq<ModListing> parseModListings() {
        return JsonIO.json.fromJson(Seq.class, ModListing.class, getModListFile().readString());
    }

    private static Fi getModListFile() {
        return getModListFile(0);
    }

    private static Fi getModListFile(int index) {
        Fi modListFile = Config.outputDirectory.child("mod-list.json");
        if (!modListFile.exists()) {
            HttpUtils.httpGetAuthorized(Vars.modJsonURLs[index], response -> {
                String strResult = response.getResultAsString();
                modListFile.writeString(strResult);
            }, error -> {
                if (index < Vars.modJsonURLs.length - 1) {
                    getModListFile(index + 1);
                } else {
                    Log.err(error);
                }
            });
        }
        return modListFile;
    }

    private static ModMeta findMeta(Fi file) {
        Fi metaFile = null;
        for (String name : metaFiles) {
            if ((metaFile = file.child(name)).exists()) {
                break;
            }
        }

        if (!metaFile.exists()) {
            return null;
        }

        ModMeta meta = json.fromJson(ModMeta.class, Jval.read(metaFile.readString()).toString(Jformat.plain));
        meta.cleanup();
        return meta;
    }

    private static Fi resolveRoot(ZipFi fi) {
        Fi[] files = fi.list();
        return files.length == 1 && files[0].isDirectory() ? files[0] : fi;
    }

    private static void githubImportMod(String repo, boolean isJava, @Nullable String release) {
        if (isJava) {
            githubImportJavaMod(repo, release);
        } else {
            HttpUtils.httpGetAuthorized(Vars.ghApi + "/repos/" + repo, res -> {
                var json = Jval.read(res.getResultAsString());
                String mainBranch = json.getString("default_branch");
                String language = json.getString("language", "<none>");

                //this is a crude heuristic for class mods; only required for direct github import
                //TODO make a more reliable way to distinguish java mod repos
                if (language.equals("Java") || language.equals("Kotlin") || language.equals("Groovy") || language.equals("Scala")) {
                    githubImportJavaMod(repo, release);
                } else {
                    githubImportBranch(mainBranch, repo, release);
                }
            }, Log::err);
        }
    }

    private static void githubImportJavaMod(String repo, @Nullable String release) {
        //grab latest release
        HttpUtils.httpGetAuthorized(Vars.ghApi + "/repos/" + repo + "/releases/" + (release == null ? "latest" : release), res -> {
            var json = Jval.read(res.getResultAsString());
            var assets = json.get("assets").asArray();

            //prioritize dexed jar, as that's what Sonnicon's mod template outputs
            var dexedAsset = assets.find(j -> j.getString("name").startsWith("dexed") && j.getString("name").endsWith(".jar"));
            var asset = dexedAsset == null ? assets.find(j -> j.getString("name").endsWith(".jar")) : dexedAsset;

            if (asset != null) {
                //grab actual file
                var url = asset.getString("browser_download_url");

                HttpUtils.httpGetAuthorized(url, result -> {
                    handleMod(repo, result);
                }, t -> {
                });
            } else {
                throw new ArcRuntimeException("No JAR file found in releases. Make sure you have a valid jar file in the mod's latest Github Release.");
            }
        }, Log::err);
    }

    private static void githubImportBranch(String branch, String repo, @Nullable String release) {
        if (release != null) {
            HttpUtils.httpGetAuthorized(Vars.ghApi + "/repos/" + repo + "/releases/" + release, res -> {
                String zipUrl = Jval.read(res.getResultAsString()).getString("zipball_url");
                HttpUtils.httpGetAuthorized(zipUrl, loc -> {
                    if (loc.getHeader("Location") != null) {
                        HttpUtils.httpGetAuthorized(loc.getHeader("Location"), result -> {
                            handleMod(repo, result);
                        }, Log::err);
                    } else {
                        handleMod(repo, loc);
                    }
                }, Log::err);
            });
        } else {
            HttpUtils.httpGetAuthorized(Vars.ghApi + "/repos/" + repo + "/zipball/" + branch, loc -> {
                if (loc.getHeader("Location") != null) {
                    HttpUtils.httpGetAuthorized(loc.getHeader("Location"), result -> {
                        handleMod(repo, result);
                    }, Log::err);
                } else {
                    handleMod(repo, loc);
                }
            }, Log::err);
        }
    }

    private static void handleMod(String repo, HttpResponse result) {
        Fi file = modDirectory().child(repo.replace("/", "") + ".zip");
        file.write(result.getResultAsStream(), false);
        if (currentModFile == null) {
            currentModFile = file;
        }
    }
}
