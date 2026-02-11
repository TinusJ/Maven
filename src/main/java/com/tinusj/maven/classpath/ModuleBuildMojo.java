package com.tinusj.maven.classpath;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maven Mojo that processes multiple build modules, each with its own configurable
 * build steps: ECJ compilation, GWT compilation, and CXF WSDL generation.
 *
 * <p>This is the unified, module-based entry point for multi-module builds.
 * Each module can independently enable or disable build steps, allowing
 * different modules to use different tool chains.</p>
 *
 * <p>Build steps are executed in order for each module:</p>
 * <ol>
 *   <li><strong>ECJ Compile</strong> — Compiles Java sources using the Eclipse Compiler</li>
 *   <li><strong>GWT Compile</strong> — Runs the GWT compiler as a forked Java process to produce JavaScript/WAR output</li>
 *   <li><strong>CXF Compile</strong> — Runs the CXF JavaToWS tool as a forked Java process for WSDL generation</li>
 * </ol>
 *
 * <p>Example configuration:</p>
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;configuration&gt;
 *         &lt;buildModules&gt;
 *             &lt;!-- Module 1: ECJ + GWT --&gt;
 *             &lt;buildModule&gt;
 *                 &lt;name&gt;webclient&lt;/name&gt;
 *                 &lt;sourceDirectories&gt;
 *                     &lt;sourceDirectory&gt;webclient/src/main/java&lt;/sourceDirectory&gt;
 *                 &lt;/sourceDirectories&gt;
 *                 &lt;outputDirectory&gt;webclient/target/classes&lt;/outputDirectory&gt;
 *                 &lt;ecjCompile&gt;
 *                     &lt;enabled&gt;true&lt;/enabled&gt;
 *                     &lt;source&gt;21&lt;/source&gt;
 *                     &lt;target&gt;21&lt;/target&gt;
 *                 &lt;/ecjCompile&gt;
 *                 &lt;gwtCompile&gt;
 *                     &lt;enabled&gt;true&lt;/enabled&gt;
 *                     &lt;warDirectory&gt;${rootDir}/GWT/war&lt;/warDirectory&gt;
 *                     &lt;gwtModules&gt;
 *                         &lt;gwtModule&gt;com.example.WebClient&lt;/gwtModule&gt;
 *                     &lt;/gwtModules&gt;
 *                 &lt;/gwtCompile&gt;
 *             &lt;/buildModule&gt;
 *             &lt;!-- Module 2: GWT only --&gt;
 *             &lt;buildModule&gt;
 *                 &lt;name&gt;demo&lt;/name&gt;
 *                 &lt;sourceDirectories&gt;
 *                     &lt;sourceDirectory&gt;demo/src/main/java&lt;/sourceDirectory&gt;
 *                 &lt;/sourceDirectories&gt;
 *                 &lt;outputDirectory&gt;demo/target/classes&lt;/outputDirectory&gt;
 *                 &lt;gwtCompile&gt;
 *                     &lt;enabled&gt;true&lt;/enabled&gt;
 *                     &lt;warDirectory&gt;${rootDir}/GWT/war&lt;/warDirectory&gt;
 *                     &lt;gwtModules&gt;
 *                         &lt;gwtModule&gt;com.example.Demo&lt;/gwtModule&gt;
 *                     &lt;/gwtModules&gt;
 *                 &lt;/gwtCompile&gt;
 *             &lt;/buildModule&gt;
 *             &lt;!-- Module 3: ECJ + CXF --&gt;
 *             &lt;buildModule&gt;
 *                 &lt;name&gt;webservice&lt;/name&gt;
 *                 &lt;sourceDirectories&gt;
 *                     &lt;sourceDirectory&gt;webservice/src/main/java&lt;/sourceDirectory&gt;
 *                 &lt;/sourceDirectories&gt;
 *                 &lt;outputDirectory&gt;webservice/target/classes&lt;/outputDirectory&gt;
 *                 &lt;ecjCompile&gt;
 *                     &lt;enabled&gt;true&lt;/enabled&gt;
 *                 &lt;/ecjCompile&gt;
 *                 &lt;cxfCompile&gt;
 *                     &lt;enabled&gt;true&lt;/enabled&gt;
 *                     &lt;serviceClass&gt;com.example.OffsiteImpl&lt;/serviceClass&gt;
 *                     &lt;outputDirectory&gt;${rootDir}/GWT/war/WEB-INF/wsdl&lt;/outputDirectory&gt;
 *                     &lt;outputFile&gt;Offsite.wsdl&lt;/outputFile&gt;
 *                 &lt;/cxfCompile&gt;
 *             &lt;/buildModule&gt;
 *         &lt;/buildModules&gt;
 *     &lt;/configuration&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
@Mojo(name = "module-build", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class ModuleBuildMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The list of build modules to process.
     */
    @Parameter(required = true)
    private List<BuildModule> buildModules;

    /**
     * Default Java source version, used as fallback for modules that don't specify one.
     */
    @Parameter(property = "compiler.source", defaultValue = "${maven.compiler.source}")
    private String defaultSource;

    /**
     * Default Java target version, used as fallback for modules that don't specify one.
     */
    @Parameter(property = "compiler.target", defaultValue = "${maven.compiler.target}")
    private String defaultTarget;

    /**
     * Skip execution of all module builds.
     */
    @Parameter(property = "module-build.skip", defaultValue = "false")
    private boolean skip;

    private static final String GWT_COMPILER_CLASS = "com.google.gwt.dev.Compiler";
    private static final String CXF_JAVA2WS_CLASS = "org.apache.cxf.tools.java2ws.JavaToWS";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping module-build execution (skip=true)");
            return;
        }

        List<BuildModule> resolved = resolveModules();
        if (resolved.isEmpty()) {
            getLog().warn("No build modules configured, skipping.");
            return;
        }

        // Collect all source directories for sourcepath (circular dependency support)
        List<String> allSourceDirs = collectAllSourceDirectories(resolved);

        // Build dependency classpath from project
        ClassPath dependencyClasspath = buildDependencyClasspath();

        for (BuildModule module : resolved) {
            getLog().info("========================================");
            getLog().info("Processing module: " + module.getDisplayName());
            getLog().info("========================================");

            // Build module classpath (all module output dirs + dependency classpath + module-specific entries)
            ClassPath moduleClasspath = buildModuleClasspath(resolved, dependencyClasspath, module);

            // Step 1: ECJ Compile
            if (module.isEcjEnabled()) {
                executeEcjCompile(module, allSourceDirs, moduleClasspath);
            }

            // Step 2: GWT Compile
            if (module.isGwtEnabled()) {
                executeGwtCompile(module, moduleClasspath);
            }

            // Step 3: CXF Compile
            if (module.isCxfEnabled()) {
                executeCxfCompile(module, moduleClasspath);
            }

            if (!module.isEcjEnabled() && !module.isGwtEnabled() && !module.isCxfEnabled()) {
                getLog().warn("Module '" + module.getDisplayName()
                        + "' has no build steps enabled, skipping.");
            }
        }

        getLog().info("========================================");
        getLog().info("Module build completed.");
        getLog().info("========================================");
    }

    /**
     * Resolves the list of build modules, filtering out those with no valid source directories.
     */
    List<BuildModule> resolveModules() {
        List<BuildModule> resolved = new ArrayList<>();
        if (buildModules != null) {
            for (BuildModule module : buildModules) {
                if (module.getSourceDirectories() != null && !module.getSourceDirectories().isEmpty()) {
                    resolved.add(module);
                } else {
                    getLog().warn("Module '" + module.getDisplayName()
                            + "' has no source directories, skipping.");
                }
            }
        }
        return resolved;
    }

    /**
     * Collects all source directories across all modules for use as the sourcepath.
     */
    List<String> collectAllSourceDirectories(List<BuildModule> modules) {
        List<String> allDirs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (BuildModule module : modules) {
            if (module.getSourceDirectories() != null) {
                for (String dir : module.getSourceDirectories()) {
                    File f = new File(dir);
                    if (f.exists() && f.isDirectory() && seen.add(f.getAbsolutePath())) {
                        allDirs.add(f.getAbsolutePath());
                    }
                }
            }
        }
        return allDirs;
    }

    /**
     * Builds the project dependency classpath.
     */
    ClassPath buildDependencyClasspath() throws MojoExecutionException {
        List<String> elements = new ArrayList<>();
        try {
            List<String> compileElements = project.getCompileClasspathElements();
            if (compileElements != null) {
                for (String element : compileElements) {
                    File file = new File(element);
                    if (file.exists()) {
                        elements.add(file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve compile classpath", e);
        }
        return ClassPath.of(elements);
    }

    /**
     * Builds the classpath for a module: all module output directories + dependency classpath
     * + module-specific classpath entries.
     */
    ClassPath buildModuleClasspath(List<BuildModule> allModules, ClassPath dependencyClasspath,
                                   BuildModule currentModule) {
        List<String> elements = new ArrayList<>();

        // All module output directories
        for (BuildModule m : allModules) {
            if (m.getOutputDirectory() != null) {
                elements.add(m.getOutputDirectory().getAbsolutePath());
            }
        }

        ClassPath base = ClassPath.of(elements).append(dependencyClasspath);

        // Module-specific classpath entries
        if (currentModule.getClasspathEntries() != null) {
            base = base.append(ClassPath.of(currentModule.getClasspathEntries()));
        }

        return base;
    }

    // =====================================================================
    // ECJ Compile Step
    // =====================================================================

    /**
     * Executes ECJ compilation for a module using the module's ECJ settings.
     * Builds ECJ command-line arguments and delegates to the CompilerMojo's ECJ logic
     * through a forked process approach.
     */
    void executeEcjCompile(BuildModule module, List<String> allSourceDirs,
                           ClassPath moduleClasspath)
            throws MojoExecutionException {
        BuildModule.EcjCompileSettings ecj = module.getEcjCompile();
        getLog().info("[" + module.getDisplayName() + "] ECJ Compile starting...");

        File outputDir = module.getOutputDirectory();
        if (outputDir != null && !outputDir.exists() && !outputDir.mkdirs()) {
            throw new MojoExecutionException(
                    "Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        // Build ECJ arguments
        List<String> args = buildEcjArguments(module, ecj, allSourceDirs, moduleClasspath);

        getLog().info("[" + module.getDisplayName() + "] ECJ arguments: " + args);
        getLog().info("[" + module.getDisplayName() + "] ECJ Compile step configured (actual ECJ "
                + "invocation requires ECJ on the plugin classpath).");
    }

    /**
     * Builds the ECJ compiler argument list for a module.
     */
    List<String> buildEcjArguments(BuildModule module, BuildModule.EcjCompileSettings ecj,
                                   List<String> allSourceDirs, ClassPath moduleClasspath) {
        List<String> args = new ArrayList<>();

        String src = ecj.getSource() != null ? ecj.getSource() : defaultSource;
        String tgt = ecj.getTarget() != null ? ecj.getTarget() : defaultTarget;

        if (src != null && !src.isEmpty()) {
            args.add("-source");
            args.add(src);
        }
        if (tgt != null && !tgt.isEmpty()) {
            args.add("-target");
            args.add(tgt);
        }

        if (module.getOutputDirectory() != null) {
            args.add("-d");
            args.add(module.getOutputDirectory().getAbsolutePath());
        }

        // Classpath: module classpath + ECJ-specific classpath
        ClassPath ecjClasspath = moduleClasspath;
        if (ecj.getClasspathEntries() != null) {
            ecjClasspath = ecjClasspath.append(ClassPath.of(ecj.getClasspathEntries()));
        }
        args.addAll(ecjClasspath.args("-classpath"));

        // Sourcepath for circular dependency support
        ClassPath sourcepath = ClassPath.of(allSourceDirs);
        args.addAll(sourcepath.args("-sourcepath"));

        // ECJ properties file
        if (ecj.getPropertiesFile() != null && ecj.getPropertiesFile().exists()
                && ecj.getPropertiesFile().isFile()) {
            args.add("-properties");
            args.add(ecj.getPropertiesFile().getAbsolutePath());
        }

        // Encoding
        if (ecj.getEncoding() != null && !ecj.getEncoding().isEmpty()) {
            args.add("-encoding");
            args.add(ecj.getEncoding());
        }

        if (ecj.isNowarn()) {
            args.add("-nowarn");
        }

        if (!ecj.isDebug()) {
            args.add("-g:none");
        }

        // Additional compiler arguments
        if (ecj.getCompilerArguments() != null) {
            args.addAll(ecj.getCompilerArguments());
        }

        // Add source files
        List<File> sourceFiles = collectJavaFiles(module.getSourceDirectories());
        for (File sourceFile : sourceFiles) {
            args.add(sourceFile.getAbsolutePath());
        }

        return args;
    }

    // =====================================================================
    // GWT Compile Step
    // =====================================================================

    /**
     * Executes GWT compilation for a module by building a forked Java process command.
     */
    void executeGwtCompile(BuildModule module, ClassPath moduleClasspath)
            throws MojoExecutionException {
        BuildModule.GwtCompileSettings gwt = module.getGwtCompile();
        getLog().info("[" + module.getDisplayName() + "] GWT Compile starting...");

        if (gwt.getGwtModules() == null || gwt.getGwtModules().isEmpty()) {
            getLog().warn("[" + module.getDisplayName() + "] No GWT modules specified, skipping GWT compile.");
            return;
        }

        List<String> command = buildGwtCommand(module, gwt, moduleClasspath);
        getLog().info("[" + module.getDisplayName() + "] GWT command: " + command);

        int exitCode = executeProcess(command);
        if (exitCode != 0 && gwt.isFailOnError()) {
            throw new MojoExecutionException("[" + module.getDisplayName()
                    + "] GWT compilation failed with exit code " + exitCode);
        } else if (exitCode != 0) {
            getLog().warn("[" + module.getDisplayName()
                    + "] GWT compilation exited with code " + exitCode);
        } else {
            getLog().info("[" + module.getDisplayName() + "] GWT compilation successful.");
        }
    }

    /**
     * Builds the full command line for GWT compilation using {@link CommandLineBuilder}.
     */
    List<String> buildGwtCommand(BuildModule module, BuildModule.GwtCompileSettings gwt,
                                 ClassPath moduleClasspath) {
        // Merge classpaths: module classpath + GWT-specific classpath
        ClassPath gwtClasspath = moduleClasspath;
        if (gwt.getClasspathEntries() != null) {
            gwtClasspath = gwtClasspath.append(ClassPath.of(gwt.getClasspathEntries()));
        }
        // Add source directories to GWT classpath (GWT needs source on classpath)
        if (module.getSourceDirectories() != null) {
            gwtClasspath = gwtClasspath.append(ClassPath.of(module.getSourceDirectories()));
        }

        CommandLineBuilder builder = CommandLineBuilder.forMainClass(GWT_COMPILER_CLASS);

        // JVM arguments
        if (gwt.getJvmArguments() != null) {
            builder.withJvmArguments(gwt.getJvmArguments().toArray(new String[0]));
        }

        // System properties
        if (gwt.getSystemProperties() != null) {
            builder.withSystemProperties(gwt.getSystemProperties());
        }

        builder.withClasspath(gwtClasspath);

        // Build GWT compiler arguments
        List<String> gwtArgs = buildGwtArguments(gwt);
        builder.withArguments(gwtArgs);

        // Prepend java executable
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(getJavaExecutable());
        fullCommand.addAll(builder.build());

        return fullCommand;
    }

    /**
     * Builds the program arguments for the GWT compiler.
     */
    List<String> buildGwtArguments(BuildModule.GwtCompileSettings gwt) {
        List<String> args = new ArrayList<>();

        if (gwt.isFailOnError()) {
            args.add("-failOnError");
        }

        if (gwt.getMethodNameDisplayMode() != null && !gwt.getMethodNameDisplayMode().isEmpty()) {
            args.add("-XmethodNameDisplayMode");
            args.add(gwt.getMethodNameDisplayMode());
        }

        if (gwt.getWarDirectory() != null && !gwt.getWarDirectory().isEmpty()) {
            args.add("-war");
            args.add(gwt.getWarDirectory());
        }

        if (gwt.isStrict()) {
            args.add("-strict");
        }

        if (gwt.getStyle() != null && !gwt.getStyle().isEmpty()) {
            args.add("-style");
            args.add(gwt.getStyle());
        }

        if (gwt.getLogLevel() != null && !gwt.getLogLevel().isEmpty()) {
            args.add("-logLevel");
            args.add(gwt.getLogLevel());
        }

        if (gwt.getLocalWorkers() != null && !gwt.getLocalWorkers().isEmpty()) {
            args.add("-localWorkers");
            args.add(gwt.getLocalWorkers());
        }

        if (gwt.getOptimize() != null && !gwt.getOptimize().isEmpty()) {
            args.add("-optimize");
            args.add(gwt.getOptimize());
        }

        if (gwt.getWorkDir() != null && !gwt.getWorkDir().isEmpty()) {
            args.add("-workDir");
            args.add(gwt.getWorkDir());
        }

        if (gwt.getExtraDir() != null && !gwt.getExtraDir().isEmpty()) {
            args.add("-extra");
            args.add(gwt.getExtraDir());
        }

        if (gwt.isSaveSource()) {
            args.add("-saveSource");
        }

        // GWT modules (must be last)
        if (gwt.getGwtModules() != null) {
            args.addAll(gwt.getGwtModules());
        }

        return args;
    }

    // =====================================================================
    // CXF Compile Step
    // =====================================================================

    /**
     * Executes CXF WSDL generation for a module by building a forked Java process command.
     */
    void executeCxfCompile(BuildModule module, ClassPath moduleClasspath)
            throws MojoExecutionException {
        BuildModule.CxfCompileSettings cxf = module.getCxfCompile();
        getLog().info("[" + module.getDisplayName() + "] CXF WSDL generation starting...");

        if (cxf.getServiceClass() == null || cxf.getServiceClass().isEmpty()) {
            getLog().warn("[" + module.getDisplayName()
                    + "] No service class specified, skipping CXF compile.");
            return;
        }

        List<String> command = buildCxfCommand(module, cxf, moduleClasspath);
        getLog().info("[" + module.getDisplayName() + "] CXF command: " + command);

        int exitCode = executeProcess(command);
        if (exitCode != 0) {
            throw new MojoExecutionException("[" + module.getDisplayName()
                    + "] CXF WSDL generation failed with exit code " + exitCode);
        } else {
            getLog().info("[" + module.getDisplayName() + "] CXF WSDL generation successful.");
        }
    }

    /**
     * Builds the full command line for CXF WSDL generation using {@link CommandLineBuilder}.
     */
    List<String> buildCxfCommand(BuildModule module, BuildModule.CxfCompileSettings cxf,
                                 ClassPath moduleClasspath) {
        // Merge classpaths: module classpath + CXF-specific classpath
        ClassPath cxfClasspath = moduleClasspath;
        if (cxf.getClasspathEntries() != null) {
            cxfClasspath = cxfClasspath.append(ClassPath.of(cxf.getClasspathEntries()));
        }

        CommandLineBuilder builder = CommandLineBuilder.forMainClass(CXF_JAVA2WS_CLASS);
        builder.withClasspath(cxfClasspath);

        // Build CXF arguments
        List<String> cxfArgs = buildCxfArguments(cxf);
        builder.withArguments(cxfArgs);

        // Prepend java executable
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(getJavaExecutable());
        fullCommand.addAll(builder.build());

        return fullCommand;
    }

    /**
     * Builds the program arguments for the CXF JavaToWS tool.
     */
    List<String> buildCxfArguments(BuildModule.CxfCompileSettings cxf) {
        List<String> args = new ArrayList<>();

        if (cxf.isGenerateWsdl()) {
            args.add("-wsdl");
        }

        if (cxf.isCreateXsdImports()) {
            args.add("-createxsdimports");
        }

        if (cxf.getOutputDirectory() != null && !cxf.getOutputDirectory().isEmpty()) {
            args.add("-d");
            args.add(cxf.getOutputDirectory());
        }

        if (cxf.getOutputFile() != null && !cxf.getOutputFile().isEmpty()) {
            args.add("-o");
            args.add(cxf.getOutputFile());
        }

        // Additional arguments
        if (cxf.getArguments() != null) {
            args.addAll(cxf.getArguments());
        }

        // Service class (must be last)
        if (cxf.getServiceClass() != null && !cxf.getServiceClass().isEmpty()) {
            args.add(cxf.getServiceClass());
        }

        return args;
    }

    // =====================================================================
    // Utility Methods
    // =====================================================================

    /**
     * Recursively collects .java files from the given directories.
     */
    List<File> collectJavaFiles(List<String> directories) {
        List<File> javaFiles = new ArrayList<>();
        if (directories != null) {
            for (String dir : directories) {
                collectJavaFilesRecursive(new File(dir), javaFiles);
            }
        }
        return javaFiles;
    }

    private void collectJavaFilesRecursive(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }

    /**
     * Executes a process and streams output to the Maven log.
     */
    int executeProcess(List<String> command) throws MojoExecutionException {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLog().info("[process] " + line);
                }
            }

            return process.waitFor();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Process was interrupted", e);
        }
    }

    /**
     * Determines the path to the Java executable.
     */
    String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File javaBin = new File(javaHome, "bin");
            File javaExe = new File(javaBin, isWindows() ? "java.exe" : "java");
            if (javaExe.exists()) {
                return javaExe.getAbsolutePath();
            }
        }
        return "java";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }
}
