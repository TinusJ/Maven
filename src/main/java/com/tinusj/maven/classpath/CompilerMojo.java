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
 * This mojo builds the classpath from project dependencies and compiles Java source files.
 * When using ECJ, a properties file can be supplied to configure compiler settings,
 * which addresses the limitation of the standard maven-compiler-plugin.
 * </p>
 * <p>
 * Usage with javac (default):
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;executions&gt;
 *         &lt;execution&gt;
 *             &lt;goals&gt;&lt;goal&gt;compile&lt;/goal&gt;&lt;/goals&gt;
 *         &lt;/execution&gt;
 *     &lt;/executions&gt;
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
     */
    @Parameter(property = "compiler.source", defaultValue = "1.8")
    private String source;

    /**
     * The Java target version (e.g. "1.8", "11", "17").
     */
    @Parameter(property = "compiler.target", defaultValue = "1.8")
    private String target;

    /**
     * Path to an ECJ properties file. Only used when compiler is set to "ecj".
     * This allows full configuration of ECJ compiler settings via a properties file,
     * such as the Eclipse JDT preferences file.
     */
    @Parameter(property = "compiler.propertiesFile")
    private File propertiesFile;

    /**
     * List of source directories to compile.
     * Defaults to the project's compile source roots.
     */
    @Parameter(property = "compiler.sourceDirectories")
    private List<String> sourceDirectories;

    /**
     * The output directory for compiled classes.
     */
    @Parameter(property = "compiler.outputDirectory",
            defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Resolve source directories
        List<String> sourceDirs = getSourceDirectories();
        if (sourceDirs.isEmpty()) {
            getLog().warn("No source directories found, skipping compilation.");
            return;
        }

        // Collect Java source files
        List<File> sourceFiles = collectJavaFiles(sourceDirs);
        if (sourceFiles.isEmpty()) {
            getLog().info("No Java source files found, skipping compilation.");
            return;
        }
        getLog().info("Found " + sourceFiles.size() + " source file(s) to compile.");

        // Build classpath
        String classpath = buildClasspath();

        // Create output directory
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new MojoExecutionException(
                    "Failed to create output directory: " + outputDirectory.getAbsolutePath());
        }

        // Compile
        String compilerType = compiler.toLowerCase().trim();
        if (COMPILER_JAVAC.equals(compilerType)) {
            compileWithJavac(sourceFiles, classpath);
        } else if (COMPILER_ECJ.equals(compilerType)) {
            compileWithEcj(sourceFiles, classpath);
        } else {
            throw new MojoExecutionException(
                    "Unsupported compiler type: " + compiler + ". Use 'javac' or 'ecj'.");
        }
    }

    /**
     * Resolves source directories from configuration or project defaults.
     */
    List<String> getSourceDirectories() {
        List<String> dirs = new ArrayList<>();
        List<String> candidates = (sourceDirectories != null && !sourceDirectories.isEmpty())
                ? sourceDirectories
                : project.getCompileSourceRoots();

        if (candidates != null) {
            for (String dir : candidates) {
                File f = new File(dir);
                if (f.exists() && f.isDirectory()) {
                    dirs.add(f.getAbsolutePath());
                    getLog().info("Using source directory: " + f.getAbsolutePath());
                } else {
                    getLog().warn("Source directory does not exist: " + dir);
                }
            }
        }
        return dirs;
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
    String buildClasspath() throws MojoExecutionException {
        List<String> elements = new ArrayList<>();
        try {
            List<String> compileElements = project.getCompileClasspathElements();
            if (compileElements != null) {
                for (String element : compileElements) {
                    File file = new File(element);
                    if (file.exists()) {
                        elements.add(file.getAbsolutePath());
                        getLog().debug("Classpath entry: " + file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve compile classpath", e);
        }

        String separator = System.getProperty("path.separator");
        String classpath = String.join(separator, elements);
        getLog().debug("Full classpath: " + classpath);
        return classpath;
    }

    /**
     * Compiles source files using the standard javac compiler via javax.tools API.
     */
    void compileWithJavac(List<File> sourceFiles, String classpath)
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

            List<String> options = buildJavacOptions(classpath);
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
    List<String> buildJavacOptions(String classpath) {
        List<String> options = new ArrayList<>();
        options.add("-source");
        options.add(source);
        options.add("-target");
        options.add(target);
        options.add("-d");
        options.add(outputDirectory.getAbsolutePath());

        if (classpath != null && !classpath.isEmpty()) {
            options.add("-classpath");
            options.add(classpath);
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
    void compileWithEcj(List<File> sourceFiles, String classpath)
            throws MojoExecutionException {
        getLog().info("Compiling with ECJ (source=" + source + ", target=" + target + ")");

        List<String> args = buildEcjArguments(sourceFiles, classpath);
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
    List<String> buildEcjArguments(List<File> sourceFiles, String classpath) {
        List<String> args = new ArrayList<>();

        args.add("-source");
        args.add(source);
        args.add("-target");
        args.add(target);
        args.add("-d");
        args.add(outputDirectory.getAbsolutePath());

        if (classpath != null && !classpath.isEmpty()) {
            args.add("-classpath");
            args.add(classpath);
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
