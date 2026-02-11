package com.tinusj.maven;

import com.tinusj.maven.config.BuildModule;
import com.tinusj.maven.support.ClassPath;
import com.tinusj.maven.support.CommandLineBuilder;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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

/**
 * Maven Mojo that launches the GWT CodeServer (SuperDevMode) for one or more
 * build modules. This allows live reloading and debugging of GWT applications
 * during development.
 *
 * <p>The CodeServer is configured at the module level, allowing different modules
 * to have independent CodeServer settings. Each module with CodeServer enabled
 * will have its source directories passed to the CodeServer via {@code -src} flags.</p>
 *
 * <p>Example configuration:</p>
 * <pre>
 * &lt;plugin&gt;
 *     &lt;groupId&gt;com.tinusj.maven&lt;/groupId&gt;
 *     &lt;artifactId&gt;classpath-plugin&lt;/artifactId&gt;
 *     &lt;configuration&gt;
 *         &lt;buildModules&gt;
 *             &lt;buildModule&gt;
 *                 &lt;name&gt;webclient&lt;/name&gt;
 *                 &lt;sourceDirectories&gt;
 *                     &lt;sourceDirectory&gt;webclient/src/main/java&lt;/sourceDirectory&gt;
 *                 &lt;/sourceDirectories&gt;
 *                 &lt;outputDirectory&gt;webclient/target/classes&lt;/outputDirectory&gt;
 *                 &lt;codeServer&gt;
 *                     &lt;enabled&gt;true&lt;/enabled&gt;
 *                     &lt;bindAddress&gt;0.0.0.0&lt;/bindAddress&gt;
 *                     &lt;port&gt;9876&lt;/port&gt;
 *                     &lt;launcherDir&gt;${rootDir}/GWT/war&lt;/launcherDir&gt;
 *                     &lt;gwtModules&gt;
 *                         &lt;gwtModule&gt;com.example.WebClient&lt;/gwtModule&gt;
 *                     &lt;/gwtModules&gt;
 *                 &lt;/codeServer&gt;
 *             &lt;/buildModule&gt;
 *         &lt;/buildModules&gt;
 *     &lt;/configuration&gt;
 *     &lt;executions&gt;
 *         &lt;execution&gt;
 *             &lt;goals&gt;&lt;goal&gt;codeserver&lt;/goal&gt;&lt;/goals&gt;
 *         &lt;/execution&gt;
 *     &lt;/executions&gt;
 * &lt;/plugin&gt;
 * </pre>
 *
 * @see <a href="https://github.com/tbroyer/gwt-maven-plugin/blob/main/src/main/java/net/ltgt/gwt/maven/CodeServerMojo.java">
 *     tbroyer GWT Maven Plugin CodeServerMojo</a>
 */
@Mojo(name = "codeserver", requiresDependencyResolution = ResolutionScope.COMPILE)
public class CodeServerMojo extends AbstractMojo {

    static final String CODESERVER_MAIN_CLASS = "com.google.gwt.dev.codeserver.CodeServer";

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
     * Global default CodeServer settings. Applied to all modules that have CodeServer enabled
     * but don't specify their own values. Module-specific settings override these defaults.
     *
     * <p>Example:</p>
     * <pre>
     * &lt;defaultCodeServer&gt;
     *     &lt;bindAddress&gt;0.0.0.0&lt;/bindAddress&gt;
     *     &lt;port&gt;9876&lt;/port&gt;
     *     &lt;logLevel&gt;INFO&lt;/logLevel&gt;
     * &lt;/defaultCodeServer&gt;
     * </pre>
     */
    @Parameter
    private BuildModule.CodeServerSettings defaultCodeServer;

    /**
     * Skip execution of this goal.
     */
    @Parameter(property = "codeserver.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping codeserver execution (skip=true)");
            return;
        }

        List<BuildModule> resolved = resolveCodeServerModules();
        if (resolved.isEmpty()) {
            getLog().warn("No modules with CodeServer enabled found, skipping.");
            return;
        }

        // Build dependency classpath from project
        ClassPath dependencyClasspath = buildDependencyClasspath();

        for (BuildModule module : resolved) {
            getLog().info("========================================");
            getLog().info("Starting CodeServer for module: " + module.getDisplayName());
            getLog().info("========================================");

            BuildModule.CodeServerSettings mergedSettings = BuildModule.CodeServerSettings.merge(
                    defaultCodeServer, module.getCodeServer());

            if (mergedSettings == null || !mergedSettings.isEnabled()) {
                continue;
            }

            // Resolve GWT modules: prefer codeServer.gwtModules, fall back to gwtCompile.gwtModules
            List<String> gwtModules = resolveGwtModules(module, mergedSettings);
            if (gwtModules == null || gwtModules.isEmpty()) {
                getLog().warn("[" + module.getDisplayName()
                        + "] No GWT modules specified for CodeServer, skipping.");
                continue;
            }

            ClassPath moduleClasspath = buildModuleClasspath(resolved, dependencyClasspath, module);

            List<String> command = buildCodeServerCommand(module, mergedSettings, moduleClasspath, gwtModules);
            getLog().info("[" + module.getDisplayName() + "] CodeServer command: " + command);

            int exitCode = executeProcess(command);
            if (exitCode != 0 && mergedSettings.isFailOnError()) {
                throw new MojoExecutionException("[" + module.getDisplayName()
                        + "] CodeServer failed with exit code " + exitCode);
            } else if (exitCode != 0) {
                getLog().warn("[" + module.getDisplayName()
                        + "] CodeServer exited with code " + exitCode);
            } else {
                getLog().info("[" + module.getDisplayName() + "] CodeServer terminated.");
            }
        }
    }

    /**
     * Resolves modules that have CodeServer enabled.
     */
    List<BuildModule> resolveCodeServerModules() {
        List<BuildModule> resolved = new ArrayList<>();
        if (buildModules != null) {
            for (BuildModule module : buildModules) {
                BuildModule.CodeServerSettings merged = BuildModule.CodeServerSettings.merge(
                        defaultCodeServer, module.getCodeServer());
                if (merged != null && merged.isEnabled()) {
                    resolved.add(module);
                }
            }
        }
        return resolved;
    }

    /**
     * Resolves the GWT modules to serve. Prefers CodeServer-specific modules,
     * falls back to GWT compile modules if configured.
     */
    List<String> resolveGwtModules(BuildModule module, BuildModule.CodeServerSettings settings) {
        if (settings.getGwtModules() != null && !settings.getGwtModules().isEmpty()) {
            return settings.getGwtModules();
        }
        // Fall back to gwtCompile modules
        if (module.getGwtCompile() != null && module.getGwtCompile().getGwtModules() != null
                && !module.getGwtCompile().getGwtModules().isEmpty()) {
            return module.getGwtCompile().getGwtModules();
        }
        return null;
    }

    /**
     * Builds the full command line for the GWT CodeServer using {@link CommandLineBuilder}.
     */
    List<String> buildCodeServerCommand(BuildModule module, BuildModule.CodeServerSettings settings,
                                        ClassPath moduleClasspath, List<String> gwtModules) {
        // Merge classpaths: module classpath + CodeServer-specific classpath
        ClassPath csClasspath = moduleClasspath;
        if (settings.getClasspathEntries() != null) {
            csClasspath = csClasspath.append(ClassPath.of(settings.getClasspathEntries()));
        }
        // Add source directories to classpath (GWT CodeServer needs source on classpath)
        if (module.getSourceDirectories() != null) {
            csClasspath = csClasspath.append(ClassPath.of(module.getSourceDirectories()));
        }

        CommandLineBuilder builder = CommandLineBuilder.forMainClass(CODESERVER_MAIN_CLASS);

        // JVM arguments
        if (settings.getJvmArguments() != null) {
            builder.withJvmArguments(settings.getJvmArguments().toArray(new String[0]));
        }

        // System properties
        if (settings.getSystemProperties() != null) {
            builder.withSystemProperties(settings.getSystemProperties());
        }

        builder.withClasspath(csClasspath);

        // Build CodeServer program arguments
        List<String> csArgs = buildCodeServerArguments(module, settings, gwtModules);
        builder.withArguments(csArgs);

        // Prepend java executable
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(getJavaExecutable());
        fullCommand.addAll(builder.build());

        return fullCommand;
    }

    /**
     * Builds the program arguments for the GWT CodeServer.
     */
    List<String> buildCodeServerArguments(BuildModule module,
                                          BuildModule.CodeServerSettings settings,
                                          List<String> gwtModules) {
        List<String> args = new ArrayList<>();

        if (settings.getWorkDir() != null && !settings.getWorkDir().isEmpty()) {
            args.add("-workDir");
            args.add(settings.getWorkDir());
        }

        if (settings.getLauncherDir() != null && !settings.getLauncherDir().isEmpty()) {
            args.add("-launcherDir");
            args.add(settings.getLauncherDir());
        }

        if (settings.getBindAddress() != null && !settings.getBindAddress().isEmpty()) {
            args.add("-bindAddress");
            args.add(settings.getBindAddress());
        }

        if (settings.getPort() != null && !settings.getPort().isEmpty()) {
            args.add("-port");
            args.add(settings.getPort());
        }

        if (settings.getLogLevel() != null && !settings.getLogLevel().isEmpty()) {
            args.add("-logLevel");
            args.add(settings.getLogLevel());
        }

        // Additional codeserver-specific arguments
        if (settings.getCodeserverArgs() != null) {
            args.addAll(settings.getCodeserverArgs());
        }

        // Allow missing sources (consistent with tbroyer plugin)
        args.add("-allowMissingSrc");

        // Source directories
        if (module.getSourceDirectories() != null) {
            for (String srcDir : module.getSourceDirectories()) {
                args.add("-src");
                args.add(srcDir);
            }
        }

        // GWT modules (must be last)
        if (gwtModules != null) {
            args.addAll(gwtModules);
        }

        return args;
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
                    getLog().info("[codeserver] " + line);
                }
            }

            return process.waitFor();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to execute CodeServer process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("CodeServer process was interrupted", e);
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
