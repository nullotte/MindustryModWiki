package wikigen.util;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Http.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.io.*;
import mindustry.mod.*;

public class ModListUtils {
    public static final int minStars = 10;

    public static ModListing currentModListing;

    public static Fi modDirectory() {
        return Config.mindustryDataDirectory.child("mods/");
    }

    public static void initMod(int index) {
        modDirectory().emptyDirectory();

        Seq<ModListing> modListings = getFilteredModListings();
        if (modListings == null || index < 0 || index >= modListings.size) return;
        ModListing modListing = modListings.get(index);

        githubImportMod(modListing.repo, modListing.hasJava, null);

        currentModListing = modListing;
    }

    public static Seq<ModListing> getFilteredModListings() {
        return parseModListings().select(m -> m.stars >= minStars);
    }

    public static Seq<ModListing> parseModListings() {
        return JsonIO.json.fromJson(Seq.class, ModListing.class, getModListFile().readString());
    }

    public static Fi getModListFile() {
        return getModListFile(0);
    }

    public static Fi getModListFile(int index) {
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

    public static void githubImportMod(String repo, boolean isJava, @Nullable String release) {
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

    public static void githubImportJavaMod(String repo, @Nullable String release) {
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

    public static void githubImportBranch(String branch, String repo, @Nullable String release) {
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

    public static void handleMod(String repo, HttpResponse result) {
        Fi file = modDirectory().child(repo.replace("/", "") + ".zip");
        file.write(result.getResultAsStream(), false);
    }
}
