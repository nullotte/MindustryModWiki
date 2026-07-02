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
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
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
import mindustry.maps.Map;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.net.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import wikigen.media.*;
import wikigen.media.Navigation.*;
import wikigen.util.*;

import java.nio.*;

public class SimulatedLauncher {
    private static final ObjectMap<String, UnlockableContent> regionToContentMap = new ObjectMap<>();
    private static final ObjectMap<UnlockableContent, Fi> contentToPageMap = new ObjectMap<>();
    private static final ObjectMap<TextureRegion, String> iconRegionToImageMap = new ObjectMap<>();

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

        if (args.length > 0) {
            if (!args[1].equals("none")) {
                HttpUtils.githubToken = args[1];
            }
            int modIndex = Strings.parseInt(args[0]);
            ModListUtils.initMod(modIndex);
        }

        UI.loadColors();
        Core.batch = new SpriteBatch();
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

        Vars.net = new Net(new ArcNetProvider());
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
        Core.assets.loadRun("mergeUI", PixmapPacker.class, () -> {
        }, () -> Fonts.mergeFontAtlas(Core.atlas));

        Vars.logic = new Logic();
        Core.assets.load(Vars.control = new Control());
        Vars.renderer = new Renderer();
        Core.assets.load(Vars.ui = new UI());
        Vars.netServer = new NetServer();
        Vars.netClient = new NetClient();

        Core.assets.load(Vars.schematics);

        Core.assets.loadRun("contentinit", ContentLoader.class, () -> Vars.content.init(), () -> Vars.content.load());
        Core.assets.loadRun("baseparts", BaseRegistry.class, () -> {
        }, () -> Vars.bases.load());

        boolean assetsLoaded;
        do {
            assetsLoaded = Core.assets.update();
        } while (!assetsLoaded);

        // PLEASE DON'T!
        Vars.fetchedServers = true;

        Seq<ApplicationListener> listeners = Seq.with(
                Vars.logic,
                Vars.control,
                Vars.renderer,
                Vars.ui,
                Vars.netServer,
                Vars.netClient
        );
        for (ApplicationListener listener : listeners) {
            listener.init();
        }
        Vars.mods.eachClass(Mod::init);
        Events.fire(new ClientLoadEvent());

        Core.bundle.getProperties().put("database-category.planet", "Planets");

        Icon.icons.each((name, drawable) -> {
            TextureRegion region = drawable.getRegion();
            Fi iconFile = Config.outputImagesDirectory.child("icon-" + name + ".png");
            if (!iconFile.exists()) {
                Pixmap pixmap = region.texture.getTextureData().getPixmap();
                if (pixmap.isDisposed()) return;

                Pixmap resultPixmap = pixmap.crop(region.getX(), region.getY(), region.width, region.height);
                iconFile.writePng(resultPixmap);
                resultPixmap.dispose();
            }
            iconRegionToImageMap.put(region, "icon-" + name);
        });

        for (ContentType contentType : ContentType.all) {
            for (Content content : Vars.content.getBy(contentType)) {
                if (content instanceof UnlockableContent unlockableContent) {
                    TextureRegion contentIconRegion = unlockableContent.uiIcon;
                    if (unlockableContent instanceof Item item && item.frames > 0) {
                        contentIconRegion = Core.atlas.find(item.name + "1");
                    }
                    if (contentIconRegion instanceof AtlasRegion atlasRegion && atlasRegion.found()) {
                        regionToContentMap.put(atlasRegion.name, unlockableContent);
                    }

                    if (unlockableContent instanceof Planet planet) {
                        Fi baseIconFile = Config.outputImagesDirectory.child("icon-" + planet.icon + ".png");
                        if (!baseIconFile.exists()) {
                            baseIconFile = Config.outputImagesDirectory.child("icon-commandRally.png");
                        }

                        Pixmap pixmap = PixmapIO.readPNG(baseIconFile);
                        pixmap.each((x, y) -> {
                            Tmp.c1.rgba8888(pixmap.get(x, y));
                            Tmp.c2.set(planet.iconColor).a(Tmp.c1.a);
                            pixmap.set(x, y, Tmp.c2);
                        });
                        Fi planetIconFile = Config.outputImagesDirectory.child("planeticon-" + planet.name + ".png");
                        planetIconFile.writePng(pixmap);
                        pixmap.dispose();
                    }
                }
            }
        }

        if (args.length > 0) {
            generateModDocs();
        }
    }

    public static void generateModDocs() {
        LoadedMod currentMod = Vars.mods.list().find(m -> m.file.equals(ModListUtils.currentModFile));

        if (currentMod == null) {
            Log.err("The initialized mod could not be found.");
            return;
        }

        if (!currentMod.enabled()) {
            Log.err("The initialized mod is not enabled, mod state is @", currentMod.state);
            return;
        }

        Fi currentModDocsDirectory = Config.outputDocsDirectory.child(
                // some mods just have empty internal names for some reason???
                !ModListUtils.currentModListing.internalName.isEmpty() ? ModListUtils.currentModListing.internalName : ModListUtils.currentModListing.name
        );
        NavSectionNode modNavNode = new NavSectionNode(currentMod.meta.displayName);

        Fi indexPage = currentModDocsDirectory.child("index.md");
        modNavNode.children.add(new NavFileNode(indexPage));

        Fi modIcon = currentMod.root.child("icon.png");
        if (!modIcon.exists()) {
            modIcon = currentMod.root.child("preview.png");
        }

        Fi modIconImage = Config.outputImagesDirectory.child("icon-" + currentModDocsDirectory.nameWithoutExtension() + ".png");
        if (modIcon.exists()) {
            modIcon.copyTo(modIconImage);
        }

        StringBuilder indexStringBuilder = new StringBuilder();

        indexStringBuilder.append("---\n");
        indexStringBuilder.append("title: ").append(Navigation.navName(currentMod.meta.displayName));
        indexStringBuilder.append("\n---\n");

        if (modIconImage.exists()) {
            indexStringBuilder.append("<img src=\"/MindustryModWiki/").append(Navigation.navPath(modIconImage)).append("\" width=\"128\" height=\"128\">\n");
        }
        indexStringBuilder.append("# ").append(Navigation.cleanName(currentMod.meta.displayName));

        indexStringBuilder
                .append("\n").append("|Property|Value|")
                .append("\n").append("|-|-|")
                .append("\n").append("|Author|").append(Navigation.cleanName(currentMod.meta.author.replace("\n", "<br>"))).append("|")
                .append("\n").append("|Repository|<https://github.com/").append(ModListUtils.currentModListing.repo).append(">|")
                .append("\n").append("|Stars|").append(ModListUtils.currentModListing.stars).append("|")
                .append("\n").append("|Last updated|").append(ModListUtils.currentModListing.lastUpdated).append("|");

        indexStringBuilder.append("\n\n").append(Strings.stripColors(currentMod.meta.description).replace("\n", "<br>"));

        indexPage.writeString(indexStringBuilder.toString());

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
                    if (u.hideDatabase) return false;

                    if (u instanceof Weather) return false;

                    if (u instanceof Block block) {
                        return block.canBeBuilt();
                    }

                    if (u instanceof Planet) {
                        return true;
                    }

                    return !u.isHidden();
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
                    file.write(); // TODO is this the correct way?
                    contentToPageMap.put(content, file);
                    tagNavSectionNode.children.add(new NavFileNode(file, content.localizedName));

                    indexDatabaseStringBuilder.append(" ")
                            .append("<a href=\"/MindustryModWiki/").append(Navigation.navPath(file).replace(".md", "/")).append("\">")
                            .append("<div class=\"content-icon-small\"><img src=\"/MindustryModWiki/images/").append(contentIcon(content)).append("\"></div>")
                            .append("</a>");
                }
            }
        }

        contentToPageMap.each(SimulatedLauncher::writeContentPage);

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
        result.append("<div class=\"content-icon\"><img src=\"/MindustryModWiki/images/").append(contentIcon(content)).append("\"></div>");
        result.append(Strings.stripColors(content.localizedName));

        if (content.description != null) {
            result.append("\n").append(Strings.stripColors(content.description));
        }

        content.checkStats();
        OrderedMap<StatCat, OrderedMap<Stat, Seq<StatValue>>> statMap = content.stats.toMap();
        if (!statMap.isEmpty()) {
            StringBuilder statsBuilder = new StringBuilder();

            statsBuilder.append("\n\n").append("|Property|Value|");
            statsBuilder.append("\n").append("|-|-|");

            statMap.each((category, map) -> {
                if (map.isEmpty()) return;
                if (content.stats.useCategories) {
                    statsBuilder.append("\n").append("|**").append(category.localized()).append("**||");
                }

                map.each((stat, statValues) -> {
                    statsBuilder.append("\n").append("|").append(Strings.stripColors(stat.localized())).append("|");
                    for (StatValue statValue : statValues) {
                        statsBuilder.append(strStat(statValue)).append(" ");
                    }
                    statsBuilder.append("|");
                });
            });

            result.append(statsBuilder);
        }

        if (content.details != null) {
            result.append("\n\n*").append(Strings.stripColors(content.details)).append("*");
        }

        file.writeString(result.toString());
    }

    public static String strStat(StatValue statValue) {
        Table dummy = new Table();
        statValue.display(dummy);
        StringBuilder result = new StringBuilder();
        display(dummy, result);
        return result.toString();
    }

    public static void display(Element element, StringBuilder stringBuilder) {
        if (element instanceof Label label) {
            String text = label.getText().toString();
            if (text.startsWith("$") || text.startsWith("@")) {
                text = Core.bundle.get(text.substring(1));
            }
            stringBuilder.append(Navigation.cleanName(text)).append(" ");
        } else if (element instanceof Image image && image.getDrawable() != null) {
            if (image.getDrawable() instanceof TextureRegionDrawable textureRegionDrawable && textureRegionDrawable.getRegion() instanceof AtlasRegion atlasRegion) {
                UnlockableContent unlockableContent = regionToContentMap.get(atlasRegion.name);
                if (unlockableContent != null) {
                    Fi page = contentToPageMap.get(unlockableContent);

                    if (page != null) {
                        stringBuilder.append("<a href=\"/MindustryModWiki/").append(Navigation.navPath(page).replace(".md", "/")).append("\">");
                    }

                    stringBuilder.append("<div class=\"content-icon-tiny\"><img src=\"/MindustryModWiki/images/").append(atlasRegion.name).append(".png\"></div>");

                    if (page != null) {
                        stringBuilder.append("</a>");
                    }

                    stringBuilder.append(" ");
                }
            }
        } else if (element instanceof Button ignored) {
        } else if (element instanceof Table t) {
            for (Cell cell : t.getCells()) {
                display(cell.get(), stringBuilder);
                if (cell.isEndRow() && element.parent == null) {
                    stringBuilder.append("<br>");
                }
            }
        } else if (element instanceof Group g) {
            for (Element child : g.getChildren()) {
                display(child, stringBuilder);
            }
        }
    }

    public static String contentIcon(UnlockableContent unlockableContent) {
        if (unlockableContent instanceof Planet planet) {
            return "planeticon-" + planet.name + ".png";
        }

        TextureRegion contentIconRegion = unlockableContent.uiIcon;
        if (unlockableContent instanceof Item item && item.frames > 0) {
            contentIconRegion = Core.atlas.find(item.name + "1");
        }
        if (contentIconRegion instanceof AtlasRegion atlasRegion) {
            return atlasRegion.name + ".png";
        }

        String iconName = iconRegionToImageMap.get(unlockableContent.uiIcon);
        if (iconName != null) {
            return iconName + ".png";
        }

        return "error.png";
    }
}
