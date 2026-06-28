package wikigen;

import arc.*;
import arc.files.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;
import mindustry.mod.*;
import wikigen.util.*;

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

        Seq<ModListing> modListings = ModListUtils.getFilteredModListings();
        for (int i = 0; i < modListings.size; i++) {
            ModListing modListing = modListings.get(i);
            if (testModName != null && !modListing.internalName.equals(testModName)) continue;

            Log.info("Loading mod " + i + "/" + modListings.size + ": " + modListing.repo);
            try {
                JavaProcess.exec(SimulatedLauncher.class, List.of(), List.of(Integer.toString(i), HttpUtils.githubToken == null ? "none" : HttpUtils.githubToken));
            } catch (Exception e) {
                Log.err(e);
            }
            Log.info("Completed mod " + i + ": " + modListing.repo);
        }
    }
}
