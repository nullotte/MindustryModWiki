package wikigen;

import arc.files.*;
import arc.struct.*;
import arc.util.*;

public class Navigation {
    public static String navName(String name) {
        return "\"" + Strings.stripColors(name.replace("\"", "\\\"").replace("\n", " ")) + "\"";
    }

    public static abstract class NavNode {
        public abstract String makeNavigation();
    }

    public static class NavSectionNode extends NavNode {
        public String name;
        public Seq<NavNode> children = new Seq<>();

        public NavSectionNode(String name) {
            this.name = name;
        }

        @Override
        public String makeNavigation() {
            if (children.isEmpty()) return "";

            StringBuilder builder = new StringBuilder();
            builder.append("\n- ").append(navName(name)).append(":");
            for (NavNode node : children) {
                builder.append(node.makeNavigation().replace("\n", "\n  "));
            }
            return builder.toString();
        }
    }

    public static class NavFileNode extends NavNode {
        public Fi file;
        public String name;

        public NavFileNode(Fi file) {
            this.file = file;
        }

        public NavFileNode(Fi file, String name) {
            this.file = file;
            this.name = name;
        }

        @Override
        public String makeNavigation() {
            return "\n- " + (name == null ? "" : (navName(name) + ": ")) + file.path().replace(Config.outputDocsDirectory.path() + "/", "");
        }
    }
}
