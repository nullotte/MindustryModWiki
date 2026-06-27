package wikigen;

import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.mod.*;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        Core.files = new MockFiles();

        if (Config.outputDirectory.exists()) {
            Config.outputDirectory.deleteDirectory();
        }

        for (Fi child : Config.baseProjectDirectory.list()) {
            child.copyTo(Config.outputProjectDirectory);
        }

        addMkDocsConfig(0, "nav:");
        addMkDocsConfig(1, "- index.md");

        Seq<ModListing> modListings = ModListUtils.parseModListings();

        for (int i = 0; i < 50; i++) {
            // TODO exoprosopa is currently index 34!
            if (i != 34) continue;
            //if (i < 33 || i > 36) continue;
            ModListing modListing = modListings.get(i);

            Log.info("Loading mod " + i + ": " + modListing.repo);

            try {
                JavaProcess.exec(SimulatedLauncher.class, List.of(), List.of(Integer.toString(i)));
            } catch (Exception e) {
                Log.err(e);
            }

            Log.info("Completed mod " + i + ": " + modListing.repo);
        }
    }

    public static void addMkDocsConfig(int level, String config) {
        Config.mkdocsConfig.writeString("\n" + " ".repeat(level * 2) + config, true);
    }
}
