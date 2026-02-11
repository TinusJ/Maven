package com.tinusj.maven;

import com.tinusj.maven.support.ClassPath;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Maven Mojo that compiles test Java sources using javac.
 * <p>
 * This goal is the test-phase equivalent of the {@code compile} goal.
 * It compiles test source files and places the compiled classes in the
 * test output directory. The test classpath includes both the project's
 * compile output and test dependencies.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;executions&gt;
 *         &lt;execution&gt;
 *             &lt;goals&gt;&lt;goal&gt;test-compile&lt;/goal&gt;&lt;/goals&gt;
 *             &lt;configuration&gt;
 *                 &lt;source&gt;21&lt;/source&gt;
 *                 &lt;target&gt;21&lt;/target&gt;
 *             &lt;/configuration&gt;
 *         &lt;/execution&gt;
 *     &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
@Mojo(name = "test-compile", defaultPhase = LifecyclePhase.TEST_COMPILE,
        requiresDependencyResolution = ResolutionScope.TEST)
public class TestCompilerMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The Java source version (e.g. "11", "17", "21").
     * Defaults to the project's maven.compiler.source property.
     */
    @Parameter(property = "testCompiler.source", defaultValue = "${maven.compiler.source}")
    private String source;

    /**
     * The Java target version (e.g. "11", "17", "21").
     * Defaults to the project's maven.compiler.target property.
     */
    @Parameter(property = "testCompiler.target", defaultValue = "${maven.compiler.target}")
    private String target;

    /**
     * Whether to show compilation warnings.
     */
    @Parameter(property = "testCompiler.showWarnings", defaultValue = "false")
    private boolean showWarnings;

    /**
     * Whether to show deprecation warnings.
     */
    @Parameter(property = "testCompiler.showDeprecation", defaultValue = "false")
    private boolean showDeprecation;

    /**
     * Additional compiler arguments to pass to the compiler.
     */
    @Parameter(property = "testCompiler.compilerArguments")
    private List<String> compilerArguments;

    /**
     * Skip execution of this goal.
     */
    @Parameter(property = "testCompiler.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Set this to {@code true} to skip test compilation entirely.
     * Bound to the standard Maven property {@code maven.test.skip}.
     */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean mavenTestSkip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip || mavenTestSkip) {
            getLog().info("Skipping test compilation (skip=true)");
            return;
        }

        // Collect test source directories
        List<String> testSourceRoots = project.getTestCompileSourceRoots();
        if (testSourceRoots == null || testSourceRoots.isEmpty()) {
            getLog().info("No test source roots found, skipping test compilation.");
            return;
        }

        // Filter to existing directories
        List<String> validSourceDirs = validateDirectories(testSourceRoots);
        if (validSourceDirs.isEmpty()) {
            getLog().info("No valid test source directories found, skipping test compilation.");
            return;
        }

        // Collect Java source files
        List<File> sourceFiles = collectJavaFiles(validSourceDirs);
        if (sourceFiles.isEmpty()) {
            getLog().info("No test Java source files found, skipping test compilation.");
            return;
        }

        // Determine output directory
        File testOutputDir = new File(project.getBuild().getTestOutputDirectory());
        if (!testOutputDir.exists() && !testOutputDir.mkdirs()) {
            throw new MojoExecutionException(
                    "Failed to create test output directory: " + testOutputDir.getAbsolutePath());
        }

        // Build test classpath
        ClassPath classpath = buildTestClasspath();

        getLog().info("Compiling " + sourceFiles.size() + " test source file(s) -> "
                + testOutputDir.getAbsolutePath());

        compileWithJavac(sourceFiles, classpath, testOutputDir);
    }

    /**
     * Compiles test source files using the standard javac compiler via javax.tools API.
     */
    void compileWithJavac(List<File> sourceFiles, ClassPath classpath, File outputDir)
            throws MojoExecutionException {
        getLog().info("Compiling test sources with javac (source=" + source + ", target=" + target + ")");

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

            List<String> options = buildJavacOptions(classpath, outputDir);
            getLog().debug("Javac options: " + options);

            JavaCompiler.CompilationTask task =
                    javac.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            boolean success = task.call();
            logDiagnostics(diagnostics);

            if (!success) {
                throw new MojoExecutionException(
                        "Test compilation failed. See above for errors.");
            }
            getLog().info("Test compilation successful.");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Error during test compilation", e);
        }
    }

    /**
     * Builds the option list for javac compilation.
     */
    List<String> buildJavacOptions(ClassPath classpath, File outputDir) {
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

        options.addAll(classpath.args("-classpath"));

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
     * Builds the test classpath from project test dependencies and compile output.
     */
    ClassPath buildTestClasspath() throws MojoExecutionException {
        List<String> elements = new ArrayList<>();
        try {
            List<String> testElements = project.getTestClasspathElements();
            if (testElements != null) {
                for (String element : testElements) {
                    File file = new File(element);
                    if (file.exists()) {
                        elements.add(file.getAbsolutePath());
                        getLog().debug("Test classpath entry: " + file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve test classpath", e);
        }
        return ClassPath.of(elements);
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
                    getLog().warn("Test source directory does not exist: " + dir);
                }
            }
        }
        return valid;
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
