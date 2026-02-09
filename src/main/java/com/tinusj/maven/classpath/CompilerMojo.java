package com.tinusj.maven.classpath;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven Mojo that compiles Java sources using either javac or ECJ (Eclipse Compiler for Java).
 * <p>
 * Supports a module-based approach where each module defines its own source directories,
 * resource directories, and output directory. All modules' source directories are made
 * available on the sourcepath so that circular dependencies between modules can be resolved,
 * while each module's source files are compiled only into its own output directory.
 * </p>
 * <p>
 * When using ECJ, a properties file can be supplied to configure compiler settings,
 * which addresses the limitation of the standard maven-compiler-plugin.
 * </p>
 * <p>
 * Usage with modules:
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;configuration&gt;
 *         &lt;modules&gt;
 *             &lt;module&gt;
 *                 &lt;sourceDirectories&gt;
 *                     &lt;sourceDirectory&gt;module-a/src/main/java&lt;/sourceDirectory&gt;
 *                 &lt;/sourceDirectories&gt;
 *                 &lt;resourceDirectories&gt;
 *                     &lt;resourceDirectory&gt;module-a/src/main/resources&lt;/resourceDirectory&gt;
 *                 &lt;/resourceDirectories&gt;
 *                 &lt;outputDirectory&gt;module-a/target/classes&lt;/outputDirectory&gt;
 *             &lt;/module&gt;
 *             &lt;module&gt;
 *                 &lt;sourceDirectories&gt;
 *                     &lt;sourceDirectory&gt;module-b/src/main/java&lt;/sourceDirectory&gt;
 *                 &lt;/sourceDirectories&gt;
 *                 &lt;outputDirectory&gt;module-b/target/classes&lt;/outputDirectory&gt;
 *             &lt;/module&gt;
 *         &lt;/modules&gt;
 *     &lt;/configuration&gt;
 * &lt;/plugin&gt;
 * </pre>
 * </p>
 * <p>
 * Usage with ECJ and a properties file:
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;configuration&gt;
 *         &lt;compiler&gt;ecj&lt;/compiler&gt;
 *         &lt;propertiesFile&gt;${project.basedir}/.settings/org.eclipse.jdt.core.prefs&lt;/propertiesFile&gt;
 *     &lt;/configuration&gt;
 *     &lt;dependencies&gt;
 *         &lt;dependency&gt;
 *             &lt;groupId&gt;org.eclipse.jdt&lt;/groupId&gt;
 *             &lt;artifactId&gt;ecj&lt;/artifactId&gt;
 *             &lt;version&gt;3.13.101&lt;/version&gt;
 *         &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 * &lt;/plugin&gt;
 * </pre>
 * </p>
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class CompilerMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The compiler to use. Supported values are "javac" and "ecj".
     */
    @Parameter(property = "compiler.type", defaultValue = "javac")
    private String compiler;

    /**
     * The Java source version (e.g. "1.8", "11", "17").
     * Defaults to the project's maven.compiler.source property.
     */
    @Parameter(property = "compiler.source", defaultValue = "${maven.compiler.source}")
    private String source;

    /**
     * The Java target version (e.g. "1.8", "11", "17").
     * Defaults to the project's maven.compiler.target property.
     */
    @Parameter(property = "compiler.target", defaultValue = "${maven.compiler.target}")
    private String target;

    /**
     * Path to an ECJ properties file. Only used when compiler is set to "ecj".
     * This allows full configuration of ECJ compiler settings via a properties file,
     * such as the Eclipse JDT preferences file.
     */
    @Parameter(property = "compiler.propertiesFile")
    private File propertiesFile;

    /**
     * List of modules to compile. Each module defines its own source directories,
     * resource directories, and output directory. All modules' source directories
     * are available on the sourcepath for resolving circular dependencies, but each
     * module's source code is compiled only into its own output directory.
     * <p>
     * If no modules are configured, defaults to a single module based on the
     * project's compile source roots and build output directory.
     * </p>
     */
    @Parameter
    private List<CompilerModule> modules;

    /**
     * Whether to show compilation warnings.
     */
    @Parameter(property = "compiler.showWarnings", defaultValue = "false")
    private boolean showWarnings;

    /**
     * Whether to show deprecation warnings.
     */
    @Parameter(property = "compiler.showDeprecation", defaultValue = "false")
    private boolean showDeprecation;

    /**
     * Additional compiler arguments to pass to the compiler.
     */
    @Parameter(property = "compiler.compilerArguments")
    private List<String> compilerArguments;

    private static final String COMPILER_JAVAC = "javac";
    private static final String COMPILER_ECJ = "ecj";
    private static final String ECJ_MAIN_CLASS = "org.eclipse.jdt.internal.compiler.batch.Main";

    /**
     * Represents a compilation module with its own source directories,
     * resource directories, and output directory.
     */
    public static class CompilerModule {
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
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Resolve modules - default to a single module from project if none configured
        List<CompilerModule> resolvedModules = resolveModules();
        if (resolvedModules.isEmpty()) {
            getLog().warn("No modules with valid source directories found, skipping compilation.");
            return;
        }

        // Collect ALL source directories across all modules (for sourcepath / circular deps)
        List<String> allSourceDirs = collectAllSourceDirectories(resolvedModules);

        // Build dependency classpath (project dependencies)
        String dependencyClasspath = buildDependencyClasspath();

        // Compile each module
        String compilerType = compiler.toLowerCase().trim();
        for (CompilerModule module : resolvedModules) {
            compileModule(module, resolvedModules, allSourceDirs, dependencyClasspath, compilerType);
        }
    }

    /**
     * Resolves the list of modules. If no modules are explicitly configured,
     * creates a default module from the project's compile source roots and output directory.
     */
    List<CompilerModule> resolveModules() {
        List<CompilerModule> resolved = new ArrayList<>();

        if (modules != null && !modules.isEmpty()) {
            for (CompilerModule module : modules) {
                List<String> validDirs = validateDirectories(module.getSourceDirectories());
                if (!validDirs.isEmpty()) {
                    resolved.add(module);
                } else {
                    getLog().warn("Module has no valid source directories, skipping.");
                }
            }
        } else {
            // Default: single module from project
            CompilerModule defaultModule = new CompilerModule();
            List<String> projectSources = project.getCompileSourceRoots();
            if (projectSources != null && !projectSources.isEmpty()) {
                defaultModule.setSourceDirectories(projectSources);
                defaultModule.setOutputDirectory(new File(project.getBuild().getOutputDirectory()));
                List<String> validDirs = validateDirectories(defaultModule.getSourceDirectories());
                if (!validDirs.isEmpty()) {
                    resolved.add(defaultModule);
                }
            }
        }

        return resolved;
    }

    /**
     * Validates directories exist and returns only existing ones.
     */
    List<String> validateDirectories(List<String> dirs) {
        List<String> valid = new ArrayList<>();
        if (dirs != null) {
            for (String dir : dirs) {
                File f = new File(dir);
                if (f.exists() && f.isDirectory()) {
                    valid.add(f.getAbsolutePath());
                } else {
                    getLog().warn("Directory does not exist: " + dir);
                }
            }
        }
        return valid;
    }

    /**
     * Collects all source directories across all modules.
     * This is used as the sourcepath so all modules can resolve each other's types
     * (supporting circular dependencies).
     */
    List<String> collectAllSourceDirectories(List<CompilerModule> moduleList) {
        List<String> allDirs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (CompilerModule module : moduleList) {
            List<String> validDirs = validateDirectories(module.getSourceDirectories());
            for (String dir : validDirs) {
                if (seen.add(dir)) {
                    allDirs.add(dir);
                    getLog().info("Sourcepath entry: " + dir);
                }
            }
        }
        return allDirs;
    }

    /**
     * Compiles a single module. All modules' source directories are passed as sourcepath
     * to resolve circular dependencies, but only this module's source files are compiled.
     */
    void compileModule(CompilerModule module, List<CompilerModule> allModules,
                       List<String> allSourceDirs,
                       String dependencyClasspath, String compilerType)
            throws MojoExecutionException {
        File moduleOutputDir = module.getOutputDirectory();
        List<String> moduleSourceDirs = validateDirectories(module.getSourceDirectories());

        // Collect Java source files for this module only
        List<File> sourceFiles = collectJavaFiles(moduleSourceDirs);
        if (sourceFiles.isEmpty()) {
            getLog().info("No Java source files found in module, skipping.");
            return;
        }

        getLog().info("Compiling module: " + sourceFiles.size() + " source file(s) -> "
                + moduleOutputDir.getAbsolutePath());

        // Create output directory
        if (!moduleOutputDir.exists() && !moduleOutputDir.mkdirs()) {
            throw new MojoExecutionException(
                    "Failed to create output directory: " + moduleOutputDir.getAbsolutePath());
        }

        // Build the full classpath: all module output directories + dependency classpath
        String classpath = buildModuleClasspath(allModules, dependencyClasspath);

        // Build the sourcepath: all modules' source directories
        String sourcepath = buildSourcepath(allSourceDirs);

        if (COMPILER_JAVAC.equals(compilerType)) {
            compileWithJavac(sourceFiles, classpath, sourcepath, moduleOutputDir);
        } else if (COMPILER_ECJ.equals(compilerType)) {
            compileWithEcj(sourceFiles, classpath, sourcepath, moduleOutputDir);
        } else {
            throw new MojoExecutionException(
                    "Unsupported compiler type: " + compiler + ". Use 'javac' or 'ecj'.");
        }
    }

    /**
     * Recursively collects .java files from the given directories.
     */
    List<File> collectJavaFiles(List<String> directories) {
        List<File> javaFiles = new ArrayList<>();
        for (String dir : directories) {
            collectJavaFilesRecursive(new File(dir), javaFiles);
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
     * Builds a classpath string from project compile dependencies.
     */
    String buildDependencyClasspath() throws MojoExecutionException {
        List<String> elements = new ArrayList<>();
        try {
            List<String> compileElements = project.getCompileClasspathElements();
            if (compileElements != null) {
                for (String element : compileElements) {
                    File file = new File(element);
                    if (file.exists()) {
                        elements.add(file.getAbsolutePath());
                        getLog().debug("Dependency classpath entry: " + file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve compile classpath", e);
        }

        String separator = System.getProperty("path.separator");
        return String.join(separator, elements);
    }

    /**
     * Builds the full module classpath by combining all module output directories
     * with the dependency classpath.
     */
    String buildModuleClasspath(List<CompilerModule> resolvedModules, String dependencyClasspath) {
        List<String> elements = new ArrayList<>();

        // Add all module output directories to classpath
        for (CompilerModule m : resolvedModules) {
            if (m.getOutputDirectory() != null) {
                elements.add(m.getOutputDirectory().getAbsolutePath());
            }
        }

        if (dependencyClasspath != null && !dependencyClasspath.isEmpty()) {
            elements.add(dependencyClasspath);
        }

        String separator = System.getProperty("path.separator");
        return String.join(separator, elements);
    }

    /**
     * Builds a sourcepath string from all source directories.
     */
    String buildSourcepath(List<String> allSourceDirs) {
        String separator = System.getProperty("path.separator");
        return String.join(separator, allSourceDirs);
    }

    /**
     * Compiles source files using the standard javac compiler via javax.tools API.
     */
    void compileWithJavac(List<File> sourceFiles, String classpath,
                          String sourcepath, File outputDir)
            throws MojoExecutionException {
        getLog().info("Compiling with javac (source=" + source + ", target=" + target + ")");

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new MojoExecutionException(
                    "No system Java compiler found. Ensure you are running with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     javac.getStandardFileManager(diagnostics, null, null)) {

            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(sourceFiles);

            List<String> options = buildJavacOptions(classpath, sourcepath, outputDir);
            getLog().debug("Javac options: " + options);

            JavaCompiler.CompilationTask task =
                    javac.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            boolean success = task.call();
            logDiagnostics(diagnostics);

            if (!success) {
                throw new MojoExecutionException("Compilation failed with javac. See above for errors.");
            }
            getLog().info("Compilation successful.");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Error during javac compilation", e);
        }
    }

    /**
     * Builds the option list for javac compilation.
     */
    List<String> buildJavacOptions(String classpath, String sourcepath, File outputDir) {
        List<String> options = new ArrayList<>();

        if (source != null && !source.isEmpty()) {
            options.add("-source");
            options.add(source);
        }

        if (target != null && !target.isEmpty()) {
            options.add("-target");
            options.add(target);
        }

        options.add("-d");
        options.add(outputDir.getAbsolutePath());

        if (classpath != null && !classpath.isEmpty()) {
            options.add("-classpath");
            options.add(classpath);
        }

        if (sourcepath != null && !sourcepath.isEmpty()) {
            options.add("-sourcepath");
            options.add(sourcepath);
        }

        if (!showWarnings) {
            options.add("-nowarn");
        }

        if (showDeprecation) {
            options.add("-deprecation");
        }

        if (compilerArguments != null) {
            options.addAll(compilerArguments);
        }

        return options;
    }

    /**
     * Compiles source files using the Eclipse Compiler for Java (ECJ).
     * ECJ must be available on the plugin's classpath (added as a plugin dependency).
     * Supports passing a properties file for ECJ configuration.
     */
    void compileWithEcj(List<File> sourceFiles, String classpath,
                        String sourcepath, File outputDir)
            throws MojoExecutionException {
        getLog().info("Compiling with ECJ (source=" + source + ", target=" + target + ")");

        List<String> args = buildEcjArguments(sourceFiles, classpath, sourcepath, outputDir);
        getLog().debug("ECJ arguments: " + args);

        URLClassLoader ecjClassLoader = null;
        try {
            EcjClassLoadResult result = loadEcjMainClass();
            ecjClassLoader = result.classLoader;
            invokeEcjCompiler(result.ecjMainClass, args);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Error during ECJ compilation", e);
        } finally {
            if (ecjClassLoader != null) {
                try {
                    ecjClassLoader.close();
                } catch (IOException e) {
                    getLog().debug("Failed to close ECJ classloader: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Builds the argument list for ECJ compilation.
     */
    List<String> buildEcjArguments(List<File> sourceFiles, String classpath,
                                   String sourcepath, File outputDir) {
        List<String> args = new ArrayList<>();

        if (source != null && !source.isEmpty()) {
            args.add("-source");
            args.add(source);
        }

        if (target != null && !target.isEmpty()) {
            args.add("-target");
            args.add(target);
        }

        args.add("-d");
        args.add(outputDir.getAbsolutePath());

        if (classpath != null && !classpath.isEmpty()) {
            args.add("-classpath");
            args.add(classpath);
        }

        if (sourcepath != null && !sourcepath.isEmpty()) {
            args.add("-sourcepath");
            args.add(sourcepath);
        }

        // ECJ properties file support - this is the key feature
        if (propertiesFile != null) {
            if (propertiesFile.exists() && propertiesFile.isFile()) {
                args.add("-properties");
                args.add(propertiesFile.getAbsolutePath());
                getLog().info("Using ECJ properties file: " + propertiesFile.getAbsolutePath());
            } else {
                getLog().warn("ECJ properties file does not exist: " + propertiesFile.getAbsolutePath());
            }
        }

        if (!showWarnings) {
            args.add("-nowarn");
        }

        if (showDeprecation) {
            args.add("-deprecation");
        }

        if (compilerArguments != null) {
            args.addAll(compilerArguments);
        }

        // Add source files
        for (File sourceFile : sourceFiles) {
            args.add(sourceFile.getAbsolutePath());
        }

        return args;
    }

    /**
     * Result of loading the ECJ Main class, including an optional classloader that must be closed.
     */
    static class EcjClassLoadResult {
        final Class<?> ecjMainClass;
        final URLClassLoader classLoader;

        EcjClassLoadResult(Class<?> ecjMainClass, URLClassLoader classLoader) {
            this.ecjMainClass = ecjMainClass;
            this.classLoader = classLoader;
        }
    }

    /**
     * Loads the ECJ Main class from the plugin's classpath.
     * Returns a result containing the loaded class and an optional classloader that must be closed.
     */
    EcjClassLoadResult loadEcjMainClass() throws MojoExecutionException {
        // First try to load from the current classloader (plugin dependencies)
        try {
            return new EcjClassLoadResult(Class.forName(ECJ_MAIN_CLASS), null);
        } catch (ClassNotFoundException e) {
            getLog().debug("ECJ not found in current classloader, trying plugin artifacts...");
        }

        // Try to load from plugin artifacts
        URLClassLoader classLoader = null;
        try {
            List<URL> urls = new ArrayList<>();
            for (Artifact artifact : project.getPluginArtifacts()) {
                if (artifact.getFile() != null) {
                    urls.add(artifact.getFile().toURI().toURL());
                }
            }

            if (!urls.isEmpty()) {
                classLoader = new URLClassLoader(
                        urls.toArray(new URL[0]), getClass().getClassLoader());
                Class<?> ecjMainClass = classLoader.loadClass(ECJ_MAIN_CLASS);
                return new EcjClassLoadResult(ecjMainClass, classLoader);
            }
        } catch (Exception e) {
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException ioe) {
                    getLog().debug("Failed to close classloader: " + ioe.getMessage());
                }
            }
            getLog().debug("Failed to load ECJ from plugin artifacts: " + e.getMessage());
        }

        throw new MojoExecutionException(
                "ECJ compiler not found. Add the ECJ dependency to the plugin configuration:\n"
                        + "<dependencies>\n"
                        + "    <dependency>\n"
                        + "        <groupId>org.eclipse.jdt</groupId>\n"
                        + "        <artifactId>ecj</artifactId>\n"
                        + "        <version>3.13.101</version>\n"
                        + "    </dependency>\n"
                        + "</dependencies>");
    }

    /**
     * Invokes the ECJ batch compiler via reflection.
     */
    void invokeEcjCompiler(Class<?> ecjMainClass, List<String> args)
            throws MojoExecutionException {
        try {
            StringWriter outWriter = new StringWriter();
            StringWriter errWriter = new StringWriter();
            PrintWriter out = new PrintWriter(outWriter);
            PrintWriter err = new PrintWriter(errWriter);

            // ECJ Main constructor: Main(PrintWriter outWriter, PrintWriter errWriter, boolean systemExitWhenFinished)
            Object ecjMain = ecjMainClass.getConstructor(
                    PrintWriter.class, PrintWriter.class, boolean.class
            ).newInstance(out, err, false);

            // ECJ Main.compile(String[] args) returns boolean
            Method compileMethod = ecjMainClass.getMethod("compile", String[].class);
            String[] argsArray = args.toArray(new String[0]);
            Boolean success = (Boolean) compileMethod.invoke(ecjMain, (Object) argsArray);

            // Log compiler output
            out.flush();
            err.flush();
            String outStr = outWriter.toString().trim();
            String errStr = errWriter.toString().trim();

            if (!outStr.isEmpty()) {
                for (String line : outStr.split("\\n")) {
                    getLog().info("[ECJ] " + line);
                }
            }
            if (!errStr.isEmpty()) {
                for (String line : errStr.split("\\n")) {
                    if (success != null && success) {
                        getLog().warn("[ECJ] " + line);
                    } else {
                        getLog().error("[ECJ] " + line);
                    }
                }
            }

            if (success == null || !success) {
                throw new MojoExecutionException("Compilation failed with ECJ. See above for errors.");
            }
            getLog().info("Compilation successful.");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to invoke ECJ compiler", e);
        }
    }

    /**
     * Logs compilation diagnostics from javac.
     */
    private void logDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        for (javax.tools.Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            String message = String.format("%s: %s (line %d in %s)",
                    diagnostic.getKind(),
                    diagnostic.getMessage(null),
                    diagnostic.getLineNumber(),
                    diagnostic.getSource() != null ? diagnostic.getSource().getName() : "unknown");

            switch (diagnostic.getKind()) {
                case ERROR:
                    getLog().error(message);
                    break;
                case WARNING:
                case MANDATORY_WARNING:
                    getLog().warn(message);
                    break;
                default:
                    getLog().info(message);
                    break;
            }
        }
    }
}
