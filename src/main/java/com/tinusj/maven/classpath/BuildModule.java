package com.tinusj.maven.classpath;

import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Represents a build module with its own source directories, output directory,
 * and configurable build steps. Each module can independently enable or disable
 * ECJ compilation, GWT compilation, and CXF WSDL generation.
 *
 * <p>Example usage in plugin configuration:</p>
 * <pre>
 * &lt;buildModules&gt;
 *     &lt;buildModule&gt;
 *         &lt;name&gt;webclient&lt;/name&gt;
 *         &lt;sourceDirectories&gt;
 *             &lt;sourceDirectory&gt;webclient/src/main/java&lt;/sourceDirectory&gt;
 *         &lt;/sourceDirectories&gt;
 *         &lt;outputDirectory&gt;webclient/target/classes&lt;/outputDirectory&gt;
 *         &lt;ecjCompile&gt;
 *             &lt;enabled&gt;true&lt;/enabled&gt;
 *             &lt;source&gt;21&lt;/source&gt;
 *             &lt;target&gt;21&lt;/target&gt;
 *         &lt;/ecjCompile&gt;
 *         &lt;gwtCompile&gt;
 *             &lt;enabled&gt;true&lt;/enabled&gt;
 *             &lt;warDirectory&gt;${rootDir}/GWT/war&lt;/warDirectory&gt;
 *             &lt;gwtModules&gt;
 *                 &lt;gwtModule&gt;com.example.WebClient&lt;/gwtModule&gt;
 *             &lt;/gwtModules&gt;
 *         &lt;/gwtCompile&gt;
 *     &lt;/buildModule&gt;
 *     &lt;buildModule&gt;
 *         &lt;name&gt;webservice&lt;/name&gt;
 *         &lt;sourceDirectories&gt;
 *             &lt;sourceDirectory&gt;webservice/src/main/java&lt;/sourceDirectory&gt;
 *         &lt;/sourceDirectories&gt;
 *         &lt;outputDirectory&gt;webservice/target/classes&lt;/outputDirectory&gt;
 *         &lt;ecjCompile&gt;
 *             &lt;enabled&gt;true&lt;/enabled&gt;
 *         &lt;/ecjCompile&gt;
 *         &lt;cxfCompile&gt;
 *             &lt;enabled&gt;true&lt;/enabled&gt;
 *             &lt;serviceClass&gt;com.example.OffsiteImpl&lt;/serviceClass&gt;
 *             &lt;outputDirectory&gt;${rootDir}/GWT/war/WEB-INF/wsdl&lt;/outputDirectory&gt;
 *             &lt;outputFile&gt;Offsite.wsdl&lt;/outputFile&gt;
 *         &lt;/cxfCompile&gt;
 *     &lt;/buildModule&gt;
 * &lt;/buildModules&gt;
 * </pre>
 */
public class BuildModule {

    /**
     * A descriptive name for this module (used in log messages).
     */
    @Parameter
    private String name;

    /**
     * Source directories for this module.
     */
    @Parameter
    private List<String> sourceDirectories;

    /**
     * Resource directories for this module.
     */
    @Parameter
    private List<String> resourceDirectories;

    /**
     * Output directory for compiled classes of this module.
     */
    @Parameter
    private File outputDirectory;

    /**
     * Additional classpath entries specific to this module.
     */
    @Parameter
    private List<String> classpathEntries;

    /**
     * ECJ compilation settings for this module.
     * If not configured or not enabled, ECJ compilation is skipped.
     */
    @Parameter
    private EcjCompileSettings ecjCompile;

    /**
     * GWT compilation settings for this module.
     * If not configured or not enabled, GWT compilation is skipped.
     */
    @Parameter
    private GwtCompileSettings gwtCompile;

    /**
     * CXF WSDL generation settings for this module.
     * If not configured or not enabled, CXF generation is skipped.
     */
    @Parameter
    private CxfCompileSettings cxfCompile;

    /**
     * WAR packaging settings for this module.
     * If not configured or not enabled, WAR packaging is skipped.
     */
    @Parameter
    private WarPackageSettings warPackage;

    // -- Getters and Setters --

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getSourceDirectories() {
        return sourceDirectories;
    }

    public void setSourceDirectories(List<String> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }

    public List<String> getResourceDirectories() {
        return resourceDirectories;
    }

    public void setResourceDirectories(List<String> resourceDirectories) {
        this.resourceDirectories = resourceDirectories;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public List<String> getClasspathEntries() {
        return classpathEntries;
    }

    public void setClasspathEntries(List<String> classpathEntries) {
        this.classpathEntries = classpathEntries;
    }

    public EcjCompileSettings getEcjCompile() {
        return ecjCompile;
    }

    public void setEcjCompile(EcjCompileSettings ecjCompile) {
        this.ecjCompile = ecjCompile;
    }

    public GwtCompileSettings getGwtCompile() {
        return gwtCompile;
    }

    public void setGwtCompile(GwtCompileSettings gwtCompile) {
        this.gwtCompile = gwtCompile;
    }

    public CxfCompileSettings getCxfCompile() {
        return cxfCompile;
    }

    public void setCxfCompile(CxfCompileSettings cxfCompile) {
        this.cxfCompile = cxfCompile;
    }

    public WarPackageSettings getWarPackage() {
        return warPackage;
    }

    public void setWarPackage(WarPackageSettings warPackage) {
        this.warPackage = warPackage;
    }

    /**
     * Returns the display name for this module, falling back to the output directory path.
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (outputDirectory != null) {
            return outputDirectory.getName();
        }
        return "unnamed";
    }

    /**
     * Returns true if this module has ECJ compilation enabled.
     */
    public boolean isEcjEnabled() {
        return ecjCompile != null && ecjCompile.isEnabled();
    }

    /**
     * Returns true if this module has GWT compilation enabled.
     */
    public boolean isGwtEnabled() {
        return gwtCompile != null && gwtCompile.isEnabled();
    }

    /**
     * Returns true if this module has CXF WSDL generation enabled.
     */
    public boolean isCxfEnabled() {
        return cxfCompile != null && cxfCompile.isEnabled();
    }

    /**
     * Returns true if this module has WAR packaging enabled.
     */
    public boolean isWarEnabled() {
        return warPackage != null && warPackage.isEnabled();
    }

    // =====================================================================
    // Nested Settings Classes
    // =====================================================================

    /**
     * ECJ (Eclipse Compiler for Java) compile settings for a module.
     *
     * <p>Configures how ECJ compiles the module's Java sources. Supports
     * ECJ-specific features like properties files and encoding settings.</p>
     */
    public static class EcjCompileSettings {

        /**
         * Whether ECJ compilation is enabled for this module.
         */
        @Parameter(defaultValue = "false")
        private boolean enabled;

        /**
         * The Java source version (e.g. "11", "17", "21").
         */
        @Parameter
        private String source;

        /**
         * The Java target version (e.g. "11", "17", "21").
         */
        @Parameter
        private String target;

        /**
         * Path to an ECJ properties file for compiler settings.
         */
        @Parameter
        private File propertiesFile;

        /**
         * Source encoding (e.g. "UTF-8", "Cp1252").
         */
        @Parameter
        private String encoding;

        /**
         * Whether to suppress warnings.
         */
        @Parameter(defaultValue = "true")
        private boolean nowarn = true;

        /**
         * Whether to fail on error.
         */
        @Parameter(defaultValue = "true")
        private boolean failOnError = true;

        /**
         * Whether to include debug information.
         */
        @Parameter(defaultValue = "true")
        private boolean debug = true;

        /**
         * Additional compiler arguments.
         */
        @Parameter
        private List<String> compilerArguments;

        /**
         * Additional classpath entries specific to ECJ compilation.
         */
        @Parameter
        private List<String> classpathEntries;

        // -- Getters and Setters --

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public File getPropertiesFile() {
            return propertiesFile;
        }

        public void setPropertiesFile(File propertiesFile) {
            this.propertiesFile = propertiesFile;
        }

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        public boolean isNowarn() {
            return nowarn;
        }

        public void setNowarn(boolean nowarn) {
            this.nowarn = nowarn;
        }

        public boolean isFailOnError() {
            return failOnError;
        }

        public void setFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
        }

        public boolean isDebug() {
            return debug;
        }

        public void setDebug(boolean debug) {
            this.debug = debug;
        }

        public List<String> getCompilerArguments() {
            return compilerArguments;
        }

        public void setCompilerArguments(List<String> compilerArguments) {
            this.compilerArguments = compilerArguments;
        }

        public List<String> getClasspathEntries() {
            return classpathEntries;
        }

        public void setClasspathEntries(List<String> classpathEntries) {
            this.classpathEntries = classpathEntries;
        }

        /**
         * Creates a new {@link EcjCompileSettings} by merging global defaults with
         * module-specific overrides. Module-specific non-null values take precedence
         * over global defaults.
         *
         * @param defaults the global default settings (may be null)
         * @param override the module-specific settings (may be null)
         * @return a merged settings instance, or null if both inputs are null
         */
        public static EcjCompileSettings merge(EcjCompileSettings defaults, EcjCompileSettings override) {
            if (defaults == null && override == null) {
                return null;
            }
            if (defaults == null) {
                return override;
            }
            if (override == null) {
                return defaults;
            }

            EcjCompileSettings merged = new EcjCompileSettings();
            merged.setEnabled(override.isEnabled());
            merged.setSource(override.getSource() != null ? override.getSource() : defaults.getSource());
            merged.setTarget(override.getTarget() != null ? override.getTarget() : defaults.getTarget());
            merged.setPropertiesFile(override.getPropertiesFile() != null ? override.getPropertiesFile() : defaults.getPropertiesFile());
            merged.setEncoding(override.getEncoding() != null ? override.getEncoding() : defaults.getEncoding());
            merged.setNowarn(override.nowarn);
            merged.setFailOnError(override.failOnError);
            merged.setDebug(override.debug);
            merged.setCompilerArguments(override.getCompilerArguments() != null ? override.getCompilerArguments() : defaults.getCompilerArguments());
            merged.setClasspathEntries(override.getClasspathEntries() != null ? override.getClasspathEntries() : defaults.getClasspathEntries());
            return merged;
        }
    }

    /**
     * GWT (Google Web Toolkit) compile settings for a module.
     *
     * <p>Configures how the GWT compiler runs as a forked Java process to
     * compile GWT modules into JavaScript for deployment in a WAR.</p>
     */
    public static class GwtCompileSettings {

        /**
         * Whether GWT compilation is enabled for this module.
         */
        @Parameter(defaultValue = "false")
        private boolean enabled;

        /**
         * The GWT modules to compile (e.g. "com.example.MyModule").
         */
        @Parameter
        private List<String> gwtModules;

        /**
         * The output war directory.
         */
        @Parameter
        private String warDirectory;

        /**
         * The GWT compile style (e.g. "OBF", "PRETTY", "DETAILED").
         */
        @Parameter(defaultValue = "OBF")
        private String style = "OBF";

        /**
         * The GWT log level (e.g. "ERROR", "WARN", "INFO", "TRACE", "DEBUG", "SPAM", "ALL").
         */
        @Parameter(defaultValue = "INFO")
        private String logLevel = "INFO";

        /**
         * Number of local workers for parallel compilation.
         */
        @Parameter
        private String localWorkers;

        /**
         * GWT optimization level (0-9).
         */
        @Parameter
        private String optimize;

        /**
         * Working directory for GWT compilation.
         */
        @Parameter
        private String workDir;

        /**
         * Extra output directory.
         */
        @Parameter
        private String extraDir;

        /**
         * Whether to save generated source.
         */
        @Parameter(defaultValue = "false")
        private boolean saveSource;

        /**
         * Whether to use strict mode.
         */
        @Parameter(defaultValue = "false")
        private boolean strict;

        /**
         * Whether to fail on error.
         */
        @Parameter(defaultValue = "true")
        private boolean failOnError = true;

        /**
         * Method name display mode (e.g. "FULL", "SHORT", "NONE").
         */
        @Parameter
        private String methodNameDisplayMode;

        /**
         * Additional classpath entries for GWT compilation.
         */
        @Parameter
        private List<String> classpathEntries;

        /**
         * JVM arguments for the forked GWT compiler process.
         */
        @Parameter
        private List<String> jvmArguments;

        /**
         * System properties for the forked GWT compiler process.
         */
        @Parameter
        private Map<String, String> systemProperties;

        // -- Getters and Setters --

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getGwtModules() {
            return gwtModules;
        }

        public void setGwtModules(List<String> gwtModules) {
            this.gwtModules = gwtModules;
        }

        public String getWarDirectory() {
            return warDirectory;
        }

        public void setWarDirectory(String warDirectory) {
            this.warDirectory = warDirectory;
        }

        public String getStyle() {
            return style;
        }

        public void setStyle(String style) {
            this.style = style;
        }

        public String getLogLevel() {
            return logLevel;
        }

        public void setLogLevel(String logLevel) {
            this.logLevel = logLevel;
        }

        public String getLocalWorkers() {
            return localWorkers;
        }

        public void setLocalWorkers(String localWorkers) {
            this.localWorkers = localWorkers;
        }

        public String getOptimize() {
            return optimize;
        }

        public void setOptimize(String optimize) {
            this.optimize = optimize;
        }

        public String getWorkDir() {
            return workDir;
        }

        public void setWorkDir(String workDir) {
            this.workDir = workDir;
        }

        public String getExtraDir() {
            return extraDir;
        }

        public void setExtraDir(String extraDir) {
            this.extraDir = extraDir;
        }

        public boolean isSaveSource() {
            return saveSource;
        }

        public void setSaveSource(boolean saveSource) {
            this.saveSource = saveSource;
        }

        public boolean isStrict() {
            return strict;
        }

        public void setStrict(boolean strict) {
            this.strict = strict;
        }

        public boolean isFailOnError() {
            return failOnError;
        }

        public void setFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
        }

        public String getMethodNameDisplayMode() {
            return methodNameDisplayMode;
        }

        public void setMethodNameDisplayMode(String methodNameDisplayMode) {
            this.methodNameDisplayMode = methodNameDisplayMode;
        }

        public List<String> getClasspathEntries() {
            return classpathEntries;
        }

        public void setClasspathEntries(List<String> classpathEntries) {
            this.classpathEntries = classpathEntries;
        }

        public List<String> getJvmArguments() {
            return jvmArguments;
        }

        public void setJvmArguments(List<String> jvmArguments) {
            this.jvmArguments = jvmArguments;
        }

        public Map<String, String> getSystemProperties() {
            return systemProperties;
        }

        public void setSystemProperties(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
        }

        /**
         * Creates a new {@link GwtCompileSettings} by merging global defaults with
         * module-specific overrides. Module-specific non-null values take precedence
         * over global defaults.
         *
         * @param defaults the global default settings (may be null)
         * @param override the module-specific settings (may be null)
         * @return a merged settings instance, or null if both inputs are null
         */
        public static GwtCompileSettings merge(GwtCompileSettings defaults, GwtCompileSettings override) {
            if (defaults == null && override == null) {
                return null;
            }
            if (defaults == null) {
                return override;
            }
            if (override == null) {
                return defaults;
            }

            GwtCompileSettings merged = new GwtCompileSettings();
            merged.setEnabled(override.isEnabled());
            merged.setGwtModules(override.getGwtModules() != null ? override.getGwtModules() : defaults.getGwtModules());
            merged.setWarDirectory(override.getWarDirectory() != null ? override.getWarDirectory() : defaults.getWarDirectory());
            merged.setStyle(override.style != null ? override.style : defaults.style);
            merged.setLogLevel(override.logLevel != null ? override.logLevel : defaults.logLevel);
            merged.setLocalWorkers(override.getLocalWorkers() != null ? override.getLocalWorkers() : defaults.getLocalWorkers());
            merged.setOptimize(override.getOptimize() != null ? override.getOptimize() : defaults.getOptimize());
            merged.setWorkDir(override.getWorkDir() != null ? override.getWorkDir() : defaults.getWorkDir());
            merged.setExtraDir(override.getExtraDir() != null ? override.getExtraDir() : defaults.getExtraDir());
            merged.setSaveSource(override.saveSource);
            merged.setStrict(override.strict);
            merged.setFailOnError(override.failOnError);
            merged.setMethodNameDisplayMode(override.getMethodNameDisplayMode() != null ? override.getMethodNameDisplayMode() : defaults.getMethodNameDisplayMode());
            merged.setClasspathEntries(override.getClasspathEntries() != null ? override.getClasspathEntries() : defaults.getClasspathEntries());
            merged.setJvmArguments(override.getJvmArguments() != null ? override.getJvmArguments() : defaults.getJvmArguments());
            merged.setSystemProperties(override.getSystemProperties() != null ? override.getSystemProperties() : defaults.getSystemProperties());
            return merged;
        }
    }

    /**
     * CXF WSDL generation settings for a module.
     *
     * <p>Configures how the CXF JavaToWS tool runs as a forked Java process to
     * generate WSDL files from Java service classes.</p>
     */
    public static class CxfCompileSettings {

        /**
         * Whether CXF WSDL generation is enabled for this module.
         */
        @Parameter(defaultValue = "false")
        private boolean enabled;

        /**
         * The fully qualified service implementation class.
         */
        @Parameter
        private String serviceClass;

        /**
         * The output directory for generated WSDL files.
         */
        @Parameter
        private String outputDirectory;

        /**
         * The output WSDL file name.
         */
        @Parameter
        private String outputFile;

        /**
         * Whether to generate WSDL output.
         */
        @Parameter(defaultValue = "true")
        private boolean generateWsdl = true;

        /**
         * Whether to create XSD imports.
         */
        @Parameter(defaultValue = "false")
        private boolean createXsdImports;

        /**
         * Additional classpath entries for CXF.
         */
        @Parameter
        private List<String> classpathEntries;

        /**
         * Additional arguments to pass to the CXF tool.
         */
        @Parameter
        private List<String> arguments;

        // -- Getters and Setters --

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceClass() {
            return serviceClass;
        }

        public void setServiceClass(String serviceClass) {
            this.serviceClass = serviceClass;
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public void setOutputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public String getOutputFile() {
            return outputFile;
        }

        public void setOutputFile(String outputFile) {
            this.outputFile = outputFile;
        }

        public boolean isGenerateWsdl() {
            return generateWsdl;
        }

        public void setGenerateWsdl(boolean generateWsdl) {
            this.generateWsdl = generateWsdl;
        }

        public boolean isCreateXsdImports() {
            return createXsdImports;
        }

        public void setCreateXsdImports(boolean createXsdImports) {
            this.createXsdImports = createXsdImports;
        }

        public List<String> getClasspathEntries() {
            return classpathEntries;
        }

        public void setClasspathEntries(List<String> classpathEntries) {
            this.classpathEntries = classpathEntries;
        }

        public List<String> getArguments() {
            return arguments;
        }

        public void setArguments(List<String> arguments) {
            this.arguments = arguments;
        }

        /**
         * Creates a new {@link CxfCompileSettings} by merging global defaults with
         * module-specific overrides. Module-specific non-null values take precedence
         * over global defaults.
         *
         * @param defaults the global default settings (may be null)
         * @param override the module-specific settings (may be null)
         * @return a merged settings instance, or null if both inputs are null
         */
        public static CxfCompileSettings merge(CxfCompileSettings defaults, CxfCompileSettings override) {
            if (defaults == null && override == null) {
                return null;
            }
            if (defaults == null) {
                return override;
            }
            if (override == null) {
                return defaults;
            }

            CxfCompileSettings merged = new CxfCompileSettings();
            merged.setEnabled(override.isEnabled());
            merged.setServiceClass(override.getServiceClass() != null ? override.getServiceClass() : defaults.getServiceClass());
            merged.setOutputDirectory(override.getOutputDirectory() != null ? override.getOutputDirectory() : defaults.getOutputDirectory());
            merged.setOutputFile(override.getOutputFile() != null ? override.getOutputFile() : defaults.getOutputFile());
            merged.setGenerateWsdl(override.generateWsdl);
            merged.setCreateXsdImports(override.createXsdImports);
            merged.setClasspathEntries(override.getClasspathEntries() != null ? override.getClasspathEntries() : defaults.getClasspathEntries());
            merged.setArguments(override.getArguments() != null ? override.getArguments() : defaults.getArguments());
            return merged;
        }
    }

    /**
     * WAR packaging settings for a module.
     *
     * <p>Creates a WAR file from a source directory. The source directory should contain
     * the web application content (e.g. WEB-INF/web.xml). Compiled classes and libraries
     * can be included from the module's output directory.</p>
     *
     * <p>Example usage:</p>
     * <pre>
     * &lt;warPackage&gt;
     *     &lt;enabled&gt;true&lt;/enabled&gt;
     *     &lt;warSourceDirectory&gt;${rootDir}/GWT/war&lt;/warSourceDirectory&gt;
     *     &lt;warFile&gt;${project.build.directory}/myapp.war&lt;/warFile&gt;
     * &lt;/warPackage&gt;
     * </pre>
     */
    public static class WarPackageSettings {

        /**
         * Whether WAR packaging is enabled for this module.
         */
        @Parameter(defaultValue = "false")
        private boolean enabled;

        /**
         * The source directory containing web application content (e.g. WEB-INF/web.xml, static files).
         * This is the root of the WAR contents.
         */
        @Parameter
        private File warSourceDirectory;

        /**
         * The output WAR file path.
         */
        @Parameter
        private File warFile;

        /**
         * Whether to include the module's compiled classes directory in WEB-INF/classes.
         */
        @Parameter(defaultValue = "true")
        private boolean includeClasses = true;

        /**
         * Additional directories whose contents should be added to the WAR root.
         * Useful for including compiled output from other steps (e.g. GWT output).
         */
        @Parameter
        private List<String> additionalContentDirectories;

        /**
         * Additional library JAR files to include in WEB-INF/lib.
         */
        @Parameter
        private List<String> libEntries;

        // -- Getters and Setters --

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public File getWarSourceDirectory() {
            return warSourceDirectory;
        }

        public void setWarSourceDirectory(File warSourceDirectory) {
            this.warSourceDirectory = warSourceDirectory;
        }

        public File getWarFile() {
            return warFile;
        }

        public void setWarFile(File warFile) {
            this.warFile = warFile;
        }

        public boolean isIncludeClasses() {
            return includeClasses;
        }

        public void setIncludeClasses(boolean includeClasses) {
            this.includeClasses = includeClasses;
        }

        public List<String> getAdditionalContentDirectories() {
            return additionalContentDirectories;
        }

        public void setAdditionalContentDirectories(List<String> additionalContentDirectories) {
            this.additionalContentDirectories = additionalContentDirectories;
        }

        public List<String> getLibEntries() {
            return libEntries;
        }

        public void setLibEntries(List<String> libEntries) {
            this.libEntries = libEntries;
        }

        /**
         * Creates a new {@link WarPackageSettings} by merging global defaults with
         * module-specific overrides. Module-specific non-null values take precedence
         * over global defaults.
         *
         * @param defaults the global default settings (may be null)
         * @param override the module-specific settings (may be null)
         * @return a merged settings instance, or null if both inputs are null
         */
        public static WarPackageSettings merge(WarPackageSettings defaults, WarPackageSettings override) {
            if (defaults == null && override == null) {
                return null;
            }
            if (defaults == null) {
                return override;
            }
            if (override == null) {
                return defaults;
            }

            WarPackageSettings merged = new WarPackageSettings();
            merged.setEnabled(override.isEnabled());
            merged.setWarSourceDirectory(override.getWarSourceDirectory() != null ? override.getWarSourceDirectory() : defaults.getWarSourceDirectory());
            merged.setWarFile(override.getWarFile() != null ? override.getWarFile() : defaults.getWarFile());
            merged.setIncludeClasses(override.includeClasses);
            merged.setAdditionalContentDirectories(override.getAdditionalContentDirectories() != null ? override.getAdditionalContentDirectories() : defaults.getAdditionalContentDirectories());
            merged.setLibEntries(override.getLibEntries() != null ? override.getLibEntries() : defaults.getLibEntries());
            return merged;
        }
    }
}
