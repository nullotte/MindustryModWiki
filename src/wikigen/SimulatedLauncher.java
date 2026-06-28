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
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import wikigen.media.*;
import wikigen.media.Navigation.*;
import wikigen.util.*;

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

        if (!args[1].equals("none")) {
            HttpUtils.githubToken = args[1];
        }
        int modIndex = Strings.parseInt(args[0]);
        ModListUtils.initMod(modIndex);

        UI.loadColors();
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

        Events.on(AtlasPackEvent.class, e -> {
            for (PageType type : PageType.all) {
                PixmapPacker packer = e.multiPacker.getPacker(type);
                for (Page page : packer.getPages()) {
                    for (int i = 0; i < page.getRects().size; i++) {
                        String name = page.getRects().orderedKeys().get(i);
                        PixmapPackerRect rect = page.getRects().get(name);
                        Pixmap result = page.getPixmap().crop((int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height);
                        Fi out = Config.outputImagesDirectory.child(name + ".png");
                        if (!out.exists()) {
                            out.writePng(result);
                        }
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

        boolean assetsLoaded;
        do {
            assetsLoaded = Core.assets.update();
        } while (!assetsLoaded);

        generateModDocs();
    }

    public static void generateModDocs() {
        LoadedMod currentMod = Vars.mods.list().find(LoadedMod::enabled);
        if (currentMod == null) {
            Log.info("This mod isn't compatible with build 158!");
            return;
        }

        Core.bundle.getProperties().put("database-category.planet", "Planets");

        Fi currentModDocsDirectory = Config.outputDocsDirectory.child(
                // some mods just have empty internal names for some reason???
                !ModListUtils.currentModListing.internalName.isEmpty() ? ModListUtils.currentModListing.internalName : ModListUtils.currentModListing.name
        );
        NavSectionNode modNavNode = new NavSectionNode(currentMod.meta.displayName);

        Fi indexPage = currentModDocsDirectory.child("index.md");
        indexPage.writeString(
                "|Property|Value|" +
                        "\n" + "|-|-|" +
                        "\n" + "|Repository|" + "<" + "https://github.com/" + ModListUtils.currentModListing.repo + ">" + "|" +
                        "\n" + "|Stars|" + ModListUtils.currentModListing.stars + "|" +
                        "\n" + "|Last updated|" + ModListUtils.currentModListing.lastUpdated + "|" +
                        "\n\n" + Strings.stripColors(currentMod.meta.description).replace("\n", "\n\n")
        );
        modNavNode.children.add(new NavFileNode(indexPage));
        StringBuilder indexDatabaseStringBuilder = new StringBuilder();

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
                Seq<UnlockableContent> array = categoryContents.get(tagName).select(u -> {
                    if (u instanceof Weather) return false;

                    if (u instanceof Block block) {
                        return block.synthetic();
                    }

                    return true;
                });
                if (array.isEmpty()) continue;
                shownCategoryContents.put(tagName, array);
            }
            if (shownCategoryContents.isEmpty()) continue;

            NavSectionNode categoryNavSectionNode = new NavSectionNode(Core.bundle.get("database-category." + categoryName));
            modNavNode.children.add(categoryNavSectionNode);
            indexDatabaseStringBuilder.append("\n").append("#### ").append(Navigation.cleanName(Core.bundle.get("database-category." + categoryName))).append("<br>");
            Fi categoryDirectory = currentModDocsDirectory.child(categoryName);
            for (int j = 0; j < shownCategoryContents.size; j++) {
                String tagName = shownCategoryContents.orderedKeys().get(j);
                Seq<UnlockableContent> array = shownCategoryContents.get(tagName);
                if (array == null || array.isEmpty()) continue;

                NavSectionNode tagNavSectionNode;
                if (tagName.equals("default")) {
                    tagNavSectionNode = categoryNavSectionNode;
                } else {
                    tagNavSectionNode = new NavSectionNode(Core.bundle.get("database-tag." + tagName));
                    categoryNavSectionNode.children.add(tagNavSectionNode);
                    indexDatabaseStringBuilder.append("\n").append("##### ").append(Navigation.cleanName(Core.bundle.get("database-tag." + tagName))).append("<br>");
                }

                Fi tagDirectory = tagName.equals("default") ? categoryDirectory : categoryDirectory.child(tagName);
                for (UnlockableContent content : array) {
                    Fi file = tagDirectory.child(content.name + ".md");
                    writeContentPage(content, file);
                    tagNavSectionNode.children.add(new NavFileNode(file, content.localizedName));

                    AtlasRegion icon = content.uiIcon.found() && content.uiIcon instanceof AtlasRegion a ? a : Core.atlas.find("error");
                    indexDatabaseStringBuilder.append(" ")
                            .append("<a href=\"/MindustryModWiki/").append(Navigation.navPath(file).replace(".md", "/")).append("\">")
                            .append("<img src=\"/MindustryModWiki/images/").append(icon.name).append(".png\" width=\"24\" height=\"24\"></img>")
                            .append("</a>");
                }
            }
        }

        // attach database
        String indexDatabaseString = indexDatabaseStringBuilder.toString();
        if (!indexDatabaseString.isEmpty()) {
            indexPage.writeString("\n## Content" + indexDatabaseString, true);
        }

        Config.mkdocsConfig.writeString(modNavNode.makeNavigation().replace("\n", "\n  "), true);
    }

    public static void writeContentPage(UnlockableContent content, Fi file) {
        StringBuilder result = new StringBuilder();

        result.append("# ");
        AtlasRegion icon = content.uiIcon.found() && content.uiIcon instanceof AtlasRegion a ? a : Core.atlas.find("error");
        result.append("<img src=\"/MindustryModWiki/images/").append(icon.name).append(".png\" width=\"48\" height=\"48\"></img> ");
        result.append(Strings.stripColors(content.localizedName));
        if (content.description != null) {
            result.append("\n").append(Strings.stripColors(content.description));
        }

        file.writeString(result.toString());
    }
}
