package wikigen;

import arc.*;
import arc.files.*;

public class Config {
    public static final Fi baseProjectDirectory = Core.files.local("base-project");
    public static final Fi outputDirectory = Core.files.local("output");
    public static final Fi mindustryDataDirectory = outputDirectory.child("mindustry-data");
    public static final Fi outputProjectDirectory = outputDirectory.child("project");
    public static final Fi outputDocsDirectory = outputProjectDirectory.child("docs");
    public static final Fi outputImagesDirectory = outputDocsDirectory.child("images");
    public static final Fi mkdocsConfig = outputProjectDirectory.child("mkdocs.yml");
}