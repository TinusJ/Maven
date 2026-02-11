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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven Mojo that runs Java annotation processing on source files.
 * <p>
 * This goal invokes the Java compiler with annotation processing enabled
 * to generate additional source files or resources based on annotations
 * in the project's source code. The generated sources are placed in a
 * configurable output directory and added as a compile source root.
 * </p>
 * <p>
 * Annotation processors can be specified explicitly via the
 * {@code annotationProcessors} parameter, or discovered automatically
 * from the {@code processorPath} via the standard
 * {@code META-INF/services/javax.annotation.processing.Processor} mechanism.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;executions&gt;
 *         &lt;execution&gt;
 *             &lt;goals&gt;&lt;goal&gt;annotation-process&lt;/goal&gt;&lt;/goals&gt;
 *             &lt;configuration&gt;
 *                 &lt;annotationProcessors&gt;
 *                     &lt;processor&gt;com.example.MyProcessor&lt;/processor&gt;
 *                 &lt;/annotationProcessors&gt;
 *                 &lt;processorPathEntries&gt;
 *                     &lt;entry&gt;${project.basedir}/lib/my-processor.jar&lt;/entry&gt;
 *                 &lt;/processorPathEntries&gt;
 *             &lt;/configuration&gt;
 *         &lt;/execution&gt;
 *     &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
@Mojo(name = "annotation-process", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class AnnotationProcessorMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The Java source version (e.g. "11", "17", "21").
     * Defaults to the project's maven.compiler.source property.
     */
    @Parameter(property = "apt.source", defaultValue = "${maven.compiler.source}")
    private String source;

    /**
     * The Java target version (e.g. "11", "17", "21").
     * Defaults to the project's maven.compiler.target property.
     */
    @Parameter(property = "apt.target", defaultValue = "${maven.compiler.target}")
    private String target;

    /**
     * List of fully qualified annotation processor class names to run.
     * If not specified, processors are discovered via the standard
     * {@code META-INF/services/javax.annotation.processing.Processor} mechanism
     * from the processor path.
     */
    @Parameter(property = "apt.processors")
    private List<String> annotationProcessors;

    /**
     * Classpath entries for annotation processor discovery and loading.
     * These JARs are searched for processor implementations.
     */
    @Parameter
    private List<String> processorPathEntries;

    /**
     * Output directory for generated source files.
     * This directory is added as a compile source root so that generated
     * sources are included in subsequent compilation.
     */
    @Parameter(property = "apt.generatedSourcesDirectory",
            defaultValue = "${project.build.directory}/generated-sources/apt")
    private File generatedSourcesDirectory;

    /**
     * Whether to show compilation warnings during annotation processing.
     */
    @Parameter(property = "apt.showWarnings", defaultValue = "false")
    private boolean showWarnings;

    /**
     * Additional compiler arguments to pass during annotation processing.
     */
    @Parameter(property = "apt.compilerArguments")
    private List<String> compilerArguments;

    /**
     * Skip execution of this goal.
     */
    @Parameter(property = "apt.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping annotation processing (skip=true)");
            return;
        }

        // Collect source directories
        List<String> sourceRoots = project.getCompileSourceRoots();
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            getLog().warn("No compile source roots found, skipping annotation processing.");
            return;
        }

        // Collect Java source files
        List<File> sourceFiles = collectJavaFiles(sourceRoots);
        if (sourceFiles.isEmpty()) {
            getLog().info("No Java source files found, skipping annotation processing.");
            return;
        }

        // Create generated sources directory
        if (!generatedSourcesDirectory.exists() && !generatedSourcesDirectory.mkdirs()) {
            throw new MojoExecutionException(
                    "Failed to create generated sources directory: "
                            + generatedSourcesDirectory.getAbsolutePath());
        }

        // Build dependency classpath
        ClassPath classpath = buildDependencyClasspath();

        // Build processor path
        ClassPath processorPath = buildProcessorPath();

        getLog().info("Running annotation processing on " + sourceFiles.size()
                + " source file(s) -> " + generatedSourcesDirectory.getAbsolutePath());

        runAnnotationProcessing(sourceFiles, classpath, processorPath);

        // Add generated sources directory as a compile source root
        project.addCompileSourceRoot(generatedSourcesDirectory.getAbsolutePath());
        getLog().info("Added generated sources directory as compile source root: "
                + generatedSourcesDirectory.getAbsolutePath());
    }

    /**
     * Runs annotation processing using the javac compiler via javax.tools API.
     */
    void runAnnotationProcessing(List<File> sourceFiles, ClassPath classpath,
                                 ClassPath processorPath) throws MojoExecutionException {
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

            List<String> options = buildOptions(classpath, processorPath);
            getLog().debug("Annotation processing options: " + options);

            JavaCompiler.CompilationTask task =
                    javac.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            boolean success = task.call();
            logDiagnostics(diagnostics);

            if (!success) {
                throw new MojoExecutionException(
                        "Annotation processing failed. See above for errors.");
            }
            getLog().info("Annotation processing completed successfully.");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Error during annotation processing", e);
        }
    }

    /**
     * Builds the option list for annotation processing.
     */
    List<String> buildOptions(ClassPath classpath, ClassPath processorPath) {
        List<String> options = new ArrayList<>();

        if (source != null && !source.isEmpty()) {
            options.add("-source");
            options.add(source);
        }

        if (target != null && !target.isEmpty()) {
            options.add("-target");
            options.add(target);
        }

        // Only run annotation processing, no compilation
        options.add("-proc:only");

        // Generated sources output directory
        options.add("-s");
        options.add(generatedSourcesDirectory.getAbsolutePath());

        options.addAll(classpath.args("-classpath"));

        options.addAll(processorPath.args("-processorpath"));

        // Specific processor classes
        if (annotationProcessors != null && !annotationProcessors.isEmpty()) {
            options.add("-processor");
            options.add(String.join(",", annotationProcessors));
        }

        if (!showWarnings) {
            options.add("-nowarn");
        }

        if (compilerArguments != null) {
            options.addAll(compilerArguments);
        }

        return options;
    }

    /**
     * Builds the processor path from configured entries.
     */
    ClassPath buildProcessorPath() {
        if (processorPathEntries == null || processorPathEntries.isEmpty()) {
            return ClassPath.empty();
        }
        return ClassPath.of(processorPathEntries);
    }

    /**
     * Builds a classpath from project compile dependencies.
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
                        getLog().debug("Dependency classpath entry: " + file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve compile classpath", e);
        }
        return ClassPath.of(elements);
    }

    /**
     * Recursively collects .java files from the given directories.
     */
    List<File> collectJavaFiles(List<String> directories) {
        List<File> javaFiles = new ArrayList<>();
        for (String dir : directories) {
            File dirFile = new File(dir);
            if (dirFile.exists() && dirFile.isDirectory()) {
                collectJavaFilesRecursive(dirFile, javaFiles);
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
