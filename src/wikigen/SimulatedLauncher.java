package wikigen;

import arc.*;
import arc.assets.*;
import arc.assets.loaders.*;
import arc.audio.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.PixmapPacker.*;
import arc.graphics.g2d.TextureAtlas.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.MultiPacker.*;
import mindustry.maps.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.net.*;
import mindustry.ui.*;

import java.nio.*;

public class SimulatedLauncher {
    public static void main(String[] args) {
        Core.settings = new MockSettings();
        Core.app = new MockApplication();
        Core.files = new MockFiles();
        Core.audio = new MockAudio();
        Core.graphics = new MockGraphics();
        Core.input = new MockInput();

        // ????
        Core.gl = new MockGL20() {
            @Override
            public int glCreateShader(int type) {
                return 1;
            }

            @Override
            public void glGetShaderiv(int shader, int pname, IntBuffer params) {
                params.put(1);
            }

            @Override
            public String glGetShaderInfoLog(int shader) {
                return "";
            }

            @Override
            public int glCreateProgram() {
                return 1;
            }

            @Override
            public void glGetProgramiv(int program, int pname, IntBuffer params) {
                params.put(1);
            }

            @Override
            public String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
                return "";
            }

            @Override
            public String glGetActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
                return "";
            }
        };

        Core.settings.setAppName("Mindustry");
        Core.settings.setDataDirectory(Config.mindustryDataDirectory);

        ArcNativesLoader.load();

        int modIndex = Strings.parseInt(args[0]);
        ModListUtils.initMod(modIndex);

        Core.assets = new AssetManager();
        Core.assets.setLoader(Texture.class, "." + Vars.mapExtension, new MapPreviewLoader());

        Core.assets.setLoader(Sound.class, new SoundLoader(Vars.tree) {
            @Override
            public void loadAsync(AssetManager manager, String fileName, Fi file, SoundParameter parameter) {

            }

            @Override
            public Sound loadSync(AssetManager manager, String fileName, Fi file, SoundParameter parameter) {
                if (parameter != null && parameter.sound != null) {
                    Vars.mainExecutor.submit(() -> parameter.sound.load(file));

                    return parameter.sound;
                } else {
                    Sound sound = new Sound();

                    Vars.mainExecutor.submit(() -> {
                        try {
                            sound.load(file);
                        } catch (Throwable t) {
                            Log.err("Error loading sound: " + file, t);
                        }
                    });

                    return sound;
                }
            }
        });
        Core.assets.setLoader(Music.class, new MusicLoader(Vars.tree) {
            @Override
            public void loadAsync(AssetManager manager, String fileName, Fi file, MusicParameter parameter) {
            }

            @Override
            public Music loadSync(AssetManager manager, String fileName, Fi file, MusicParameter parameter) {
                if (parameter != null && parameter.music != null) {
                    Vars.mainExecutor.submit(() -> {
                        try {
                            parameter.music.load(file);
                        } catch (Throwable t) {
                            Log.err("Error loading music: " + file, t);
                        }
                    });

                    return parameter.music;
                } else {
                    Music music = new Music();

                    Vars.mainExecutor.submit(() -> {
                        try {
                            music.load(file);
                        } catch (Throwable t) {
                            Log.err("Error loading music: " + file, t);
                        }
                    });

                    return music;
                }
            }
        });

        Core.assets.load("sprites/error.png", Texture.class);
        Core.atlas = TextureAtlas.blankAtlas();

        Vars.net = new Net(null);
        MapPreviewLoader.setupLoaders();
        Vars.mods = new Mods();
        Vars.schematics = new Schematics();

        Fonts.loadSystemCursors();

        Core.assets.load(new Vars());

        Fonts.loadDefaultFont();

        Core.assets.load(new AssetDescriptor<>("sprites/sprites.aatls", TextureAtlas.class)).loaded = t -> Core.atlas = t;
        Core.assets.loadRun("maps", Map.class, () -> Vars.maps.loadPreviews());

        Musics.load();
        Sounds.load();

        Core.assets.loadRun("contentcreate", Content.class, () -> {
            Vars.content.createBaseContent();
            Vars.content.loadColors();
        }, () -> {
            Vars.mods.loadScripts();
            Vars.content.createModContent();
        });

        // this is horrible and includes a lot of unnecessary images!
        Events.on(AtlasPackEvent.class, e -> {
            Fi modImages = Config.outputDocsDirectory.child(ModListUtils.currentModListing.internalName).child("images");

            for (PageType type : PageType.all) {
                PixmapPacker packer = e.multiPacker.getPacker(type);
                for (Page page : packer.getPages()) {
                    for (int i = 0; i < page.getRects().size; i++) {
                        String name = page.getRects().orderedKeys().get(i);
                        PixmapPackerRect rect = page.getRects().get(name);
                        Pixmap result = page.getPixmap().crop((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height);
                        modImages.child(name + ".png").writePng(result);
                        result.dispose();
                    }
                }
            }
        });

        Core.assets.load(Vars.mods);
        Core.assets.loadRun("mergeUI", PixmapPacker.class, () -> {}, () -> Fonts.mergeFontAtlas(Core.atlas));

        Vars.logic = new Logic();
        Core.assets.load(Vars.control = new Control());
        Vars.renderer = new Renderer();
        Core.assets.load(Vars.ui = new UI());
        Vars.netServer = new NetServer();
        Vars.netClient = new NetClient();

        Core.assets.load(Vars.schematics);

        Core.assets.loadRun("contentinit", ContentLoader.class, () -> Vars.content.init(), () -> Vars.content.load());
        Core.assets.loadRun("baseparts", BaseRegistry.class, () -> {}, () -> Vars.bases.load());

        while (!Core.assets.update());

        generateModDocs();
    }

    public static void generateModDocs() {
        LoadedMod currentMod = Vars.mods.list().find(LoadedMod::enabled);
        if (currentMod == null) {
            Log.info("this mod isn't compatible with v158!");
            return;
        }

        Fi currentModDocsDirectory = Config.outputDocsDirectory.child(ModListUtils.currentModListing.internalName);

        Fi indexPage = currentModDocsDirectory.child("index.md");
        indexPage.writeString(
                "|Property|Value|" +
                        "\n" + "|-|-|" +
                        "\n" + "|Repository|" + "<" + "https://github.com/" + ModListUtils.currentModListing.repo + ">" + "|" +
                        "\n" + "|Stars|" + ModListUtils.currentModListing.stars + "|" +
                        "\n" + "|Last updated|" + ModListUtils.currentModListing.lastUpdated + "|" +
                        "\n\n" + Strings.stripColors(currentMod.meta.description).replace("\n", "\n\n")
        );

        OrderedMap<String, OrderedMap<String, Seq<UnlockableContent>>> sortedContents = new OrderedMap<>();

        Seq<Content>[] allContent = Vars.content.getContentMap();
        for (Seq<Content> contents : allContent) {
            for (Content content : contents) {
                if (content instanceof UnlockableContent unlockableContent && content.minfo.mod == currentMod) {
                    String cat = unlockableContent.databaseCategory == null ? unlockableContent.getContentType().name() : unlockableContent.databaseCategory;
                    String tag = unlockableContent.databaseTag == null ? "default" : unlockableContent.databaseTag;

                    OrderedMap<String, Seq<UnlockableContent>> categoryContents = sortedContents.get(cat, new OrderedMap<>());
                    Seq<UnlockableContent> taggedContents = categoryContents.get(tag, new Seq<>());
                    taggedContents.add(unlockableContent);
                    categoryContents.put(tag, taggedContents);
                    sortedContents.put(cat, categoryContents);
                }
            }
        }

        OrderedMap<String, Seq<UnlockableContent>> shownCategoryContents = new OrderedMap<>();
        for (int i = 0; i < sortedContents.size; i++) {
            String categoryName = sortedContents.orderedKeys().get(i);
            OrderedMap<String, Seq<UnlockableContent>> categoryContents = sortedContents.get(categoryName);

            shownCategoryContents.clear();
            for (int j = 0; j < categoryContents.size; j++) {
                String tagName = categoryContents.orderedKeys().get(j);
                // TODO visibilities!
                Seq<UnlockableContent> array = categoryContents.get(tagName);//.select(u -> !u.isHidden() && !u.hideDatabase);
                if (array.isEmpty()) continue;
                shownCategoryContents.put(tagName, array);
            }
            if (shownCategoryContents.isEmpty()) continue;

            Fi categoryDirectory = currentModDocsDirectory.child(categoryName);
            for (int j = 0; j < shownCategoryContents.size; j++) {
                String tagName = shownCategoryContents.orderedKeys().get(j);
                Seq<UnlockableContent> array = shownCategoryContents.get(tagName);
                if (array == null || array.isEmpty()) continue;

                Fi tagDirectory = tagName.equals("default") ? categoryDirectory : categoryDirectory.child(tagName);
                for (UnlockableContent content : array) {
                    StringBuilder result = new StringBuilder();

                    result.append("# ");
                    TextureRegion uiIcon = content.uiIcon;
                    if (uiIcon instanceof AtlasRegion a) {
                        result.append("<img src=\"/").append(ModListUtils.currentModListing.internalName).append("/images/").append(a.name).append(".png\" width=\"48\" height=\"48\"></img> ");
                    }
                    result.append(content.localizedName);
                    result.append("\n");
                    if (content.description != null) {
                        result.append(Strings.stripColors(content.description));
                    } else {
                        result.append("...");
                    }

                    Fi file = tagDirectory.child(content.name + ".md");
                    file.writeString(result.toString());
                }
            }
        }

        // add navigations
        addMkDocsConfig(1, "- " + navName(Strings.stripColors(currentMod.meta.displayName)) + ":");
        addMkDocsConfig(2, "- " + navName(Strings.stripColors(currentMod.meta.displayName)) + ": " + getNavPath(indexPage));
        for (Fi category : currentModDocsDirectory.list()) {
            if (!category.isDirectory()) continue;
            addMkDocsConfig(2, "- " + navName(Core.bundle.get("database-category." + category.nameWithoutExtension())) + ": ");
            for (Fi file : category.list()) {
                if (file.isDirectory()) {
                    addMkDocsConfig(3, "- " + navName(Core.bundle.get("database-tag." + file.nameWithoutExtension())) + ":");
                    for (Fi content : file.list()) {
                        MappableContent m = Vars.content.byName(content.nameWithoutExtension());
                        if (m instanceof UnlockableContent u) {
                            addMkDocsConfig(4, "- " + navName(u.localizedName) + ": " + getNavPath(content));
                        }
                    }
                } else {
                    MappableContent m = Vars.content.byName(file.nameWithoutExtension());
                    if (m instanceof UnlockableContent u) {
                        addMkDocsConfig(3, "- " + navName(u.localizedName) + ": " + getNavPath(file));
                    }
                }
            }
        }
    }

    public static String getNavPath(Fi file) {
        return file.path().replace(Config.outputDocsDirectory.path() + "/", "");
    }

    public static String navName(String name) {
        return "\"" + name.replace("\"", "\\\"") + "\"";
    }

    // ???
    public static void addMkDocsConfig(int level, String config) {
        Main.addMkDocsConfig(level, config);
    }
}
