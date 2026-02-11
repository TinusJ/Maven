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
 * Maven Mojo that executes a forked Java process with configurable classpath,
 * JVM arguments, and program arguments. This supports use cases such as:
 * <ul>
 *   <li>GWT compilation ({@code com.google.gwt.dev.Compiler})</li>
 *   <li>CXF WSDL generation ({@code org.apache.cxf.tools.java2ws.JavaToWS})</li>
 *   <li>Any other Java main class that needs to be run as part of the build</li>
 * </ul>
 *
 * <p>Example usage for GWT compilation:</p>
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;executions&gt;
 *         &lt;execution&gt;
 *             &lt;goals&gt;&lt;goal&gt;java-process&lt;/goal&gt;&lt;/goals&gt;
 *             &lt;configuration&gt;
 *                 &lt;mainClass&gt;com.google.gwt.dev.Compiler&lt;/mainClass&gt;
 *                 &lt;classpathEntries&gt;
 *                     &lt;entry&gt;${project.basedir}/lib/gwt-dev.jar&lt;/entry&gt;
 *                     &lt;entry&gt;${project.basedir}/lib/gwt-user.jar&lt;/entry&gt;
 *                 &lt;/classpathEntries&gt;
 *                 &lt;jvmArguments&gt;
 *                     &lt;arg&gt;-Djava.io.tmpdir=${webtempfolder}&lt;/arg&gt;
 *                 &lt;/jvmArguments&gt;
 *                 &lt;arguments&gt;
 *                     &lt;arg&gt;-failOnError&lt;/arg&gt;
 *                     &lt;arg&gt;-war&lt;/arg&gt;
 *                     &lt;arg&gt;${rootDir}/GWT/war&lt;/arg&gt;
 *                     &lt;arg&gt;-strict&lt;/arg&gt;
 *                     &lt;arg&gt;-style&lt;/arg&gt;
 *                     &lt;arg&gt;OBF&lt;/arg&gt;
 *                     &lt;arg&gt;com.example.MyModule&lt;/arg&gt;
 *                 &lt;/arguments&gt;
 *             &lt;/configuration&gt;
 *         &lt;/execution&gt;
 *     &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 *
 * <p>Example usage for CXF WSDL generation:</p>
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;executions&gt;
 *         &lt;execution&gt;
 *             &lt;goals&gt;&lt;goal&gt;java-process&lt;/goal&gt;&lt;/goals&gt;
 *             &lt;configuration&gt;
 *                 &lt;mainClass&gt;org.apache.cxf.tools.java2ws.JavaToWS&lt;/mainClass&gt;
 *                 &lt;classpathEntries&gt;
 *                     &lt;entry&gt;${project.basedir}/lib/cxf-tools.jar&lt;/entry&gt;
 *                 &lt;/classpathEntries&gt;
 *                 &lt;arguments&gt;
 *                     &lt;arg&gt;-wsdl&lt;/arg&gt;
 *                     &lt;arg&gt;-createxsdimports&lt;/arg&gt;
 *                     &lt;arg&gt;-d&lt;/arg&gt;
 *                     &lt;arg&gt;${rootDir}/GWT/war/offsitewebservice/WEB-INF/wsdl&lt;/arg&gt;
 *                     &lt;arg&gt;-o&lt;/arg&gt;
 *                     &lt;arg&gt;Offsite.wsdl&lt;/arg&gt;
 *                     &lt;arg&gt;com.example.OffsiteImpl&lt;/arg&gt;
 *                 &lt;/arguments&gt;
 *             &lt;/configuration&gt;
 *         &lt;/execution&gt;
 *     &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
@Mojo(name = "java-process", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class JavaProcessMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The fully qualified main class to execute.
     */
    @Parameter(property = "javaprocess.mainClass", required = true)
    private String mainClass;

    /**
     * Additional classpath entries for the forked process.
     * These are combined with project dependencies when {@code includeProjectClasspath} is true.
     */
    @Parameter
    private List<String> classpathEntries;

    /**
     * Whether to include the project's compile classpath in the forked process classpath.
     * Defaults to true.
     */
    @Parameter(property = "javaprocess.includeProjectClasspath", defaultValue = "true")
    private boolean includeProjectClasspath;

    /**
     * JVM arguments for the forked process (e.g. {@code -Xmx512m}, {@code -Djava.io.tmpdir=/tmp}).
     */
    @Parameter
    private List<String> jvmArguments;

    /**
     * System properties to pass to the forked process.
     * Each entry becomes a {@code -Dkey=value} JVM argument.
     */
    @Parameter
    private Map<String, String> systemProperties;

    /**
     * Program arguments to pass to the main class.
     */
    @Parameter
    private List<String> arguments;

    /**
     * The working directory for the forked process.
     * Defaults to the project base directory.
     */
    @Parameter(property = "javaprocess.workingDirectory", defaultValue = "${project.basedir}")
    private File workingDirectory;

    /**
     * Whether to fail the build if the forked process returns a non-zero exit code.
     * Defaults to true.
     */
    @Parameter(property = "javaprocess.failOnError", defaultValue = "true")
    private boolean failOnError;

    /**
     * Skip execution of this goal.
     */
    @Parameter(property = "javaprocess.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping java-process execution (skip=true)");
            return;
        }

        if (mainClass == null || mainClass.isEmpty()) {
            throw new MojoExecutionException("mainClass is required");
        }

        List<String> command = buildCommand();
        getLog().info("Executing: " + mainClass);
        getLog().debug("Full command: " + command);

        int exitCode = executeProcess(command);

        if (exitCode != 0 && failOnError) {
            throw new MojoExecutionException(
                    "Java process failed with exit code " + exitCode + ": " + mainClass);
        } else if (exitCode != 0) {
            getLog().warn("Java process exited with code " + exitCode + ": " + mainClass);
        } else {
            getLog().info("Java process completed successfully: " + mainClass);
        }
    }

    /**
     * Builds the full command line for the forked Java process using {@link CommandLineBuilder}.
     * @return the complete command line as a list of strings
     * @throws MojoExecutionException if the classpath cannot be resolved
     */
    List<String> buildCommand() throws MojoExecutionException {
        String javaExecutable = getJavaExecutable();

        CommandLineBuilder builder = CommandLineBuilder.forMainClass(mainClass);

        // Add JVM arguments
        if (jvmArguments != null) {
            builder.withJvmArguments(jvmArguments.toArray(new String[0]));
        }

        // Add system properties
        if (systemProperties != null) {
            builder.withSystemProperties(systemProperties);
        }

        // Build classpath
        ClassPath classPath = buildClasspath();
        builder.withClasspath(classPath);

        // Add program arguments
        if (arguments != null) {
            builder.withArguments(arguments);
        }

        // Prepend the java executable
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(javaExecutable);
        fullCommand.addAll(builder.build());

        return fullCommand;
    }

    /**
     * Builds the classpath for the forked process by combining project dependencies
     * and configured classpath entries.
     */
    ClassPath buildClasspath() throws MojoExecutionException {
        List<String> elements = new ArrayList<>();

        // Add project compile classpath if requested
        if (includeProjectClasspath) {
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
        }

        // Add configured classpath entries
        if (classpathEntries != null) {
            for (String entry : classpathEntries) {
                if (entry != null && !entry.isEmpty()) {
                    elements.add(entry);
                }
            }
        }

        return ClassPath.of(elements);
    }

    /**
     * Executes the process and streams output to the Maven log.
     * @param command the command line to execute
     * @return the process exit code
     * @throws MojoExecutionException if the process cannot be started
     */
    int executeProcess(List<String> command) throws MojoExecutionException {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);

            if (workingDirectory != null && workingDirectory.exists()) {
                processBuilder.directory(workingDirectory);
            }

            Process process = processBuilder.start();

            // Stream output to Maven log
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLog().info("[java-process] " + line);
                }
            }

            return process.waitFor();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute Java process: " + mainClass, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Java process was interrupted: " + mainClass, e);
        }
    }

    /**
     * Determines the path to the Java executable.
     * @return the path to the java executable
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
