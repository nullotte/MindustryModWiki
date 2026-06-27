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
        if (args.length > 0 && !args[0].equals("none")) {
            HttpUtils.githubToken = args[0];
        }

        String testModName = null;
        if (args.length > 1) {
            testModName = args[1];
        }

        Core.files = new MockFiles();

        if (Config.outputDirectory.exists()) {
            Config.outputDirectory.deleteDirectory();
        }

        for (Fi child : Config.baseProjectDirectory.list()) {
            child.copyTo(Config.outputProjectDirectory);
        }

        Config.addMkDocsConfig(0, "nav:");
        Config.addMkDocsConfig(1, "- index.md");

        Seq<ModListing> modListings = ModListUtils.parseModListings();
        for (int i = 0; i < modListings.size; i++) {
            ModListing modListing = modListings.get(i);
            if (testModName != null && !modListing.internalName.equals(testModName)) continue;
            if (modListing.stars < 10) continue;

            Log.info("Loading mod " + i + ": " + modListing.repo);
            try {
                JavaProcess.exec(SimulatedLauncher.class, List.of(), List.of(Integer.toString(i), HttpUtils.githubToken == null ? "none" : HttpUtils.githubToken));
            } catch (Exception e) {
                Log.err(e);
            }
            Log.info("Completed mod " + i + ": " + modListing.repo);
        }
    }
}
