package com.tinusj.maven.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper class to build the command-line arguments of a forked Java process.
 * <p>
 * Inspired by Spring Boot's CommandLineBuilder, this class provides a fluent API
 * for constructing command lines that can be used to invoke a Java process with:
 * </p>
 * <ul>
 *   <li>JVM arguments (e.g. {@code -Xmx512m}, {@code -Djava.io.tmpdir=/tmp})</li>
 *   <li>System properties (formatted as {@code -Dkey=value})</li>
 *   <li>Classpath elements</li>
 *   <li>A main class</li>
 *   <li>Program arguments</li>
 * </ul>
 *
 * <p>Example usage for GWT compilation:</p>
 * <pre>
 * List&lt;String&gt; command = CommandLineBuilder.forMainClass("com.google.gwt.dev.Compiler")
 *     .withJvmArguments("-Djava.io.tmpdir=/tmp/gwt")
 *     .withClasspath(gwtClassPath)
 *     .withArguments("-failOnError", "-war", warDir, "-strict")
 *     .build();
 * </pre>
 *
 * <p>Example usage for CXF WSDL generation:</p>
 * <pre>
 * List&lt;String&gt; command = CommandLineBuilder.forMainClass("org.apache.cxf.tools.java2ws.JavaToWS")
 *     .withClasspath(cxfClassPath)
 *     .withArguments("-wsdl", "-createxsdimports", "-d", wsdlDir, "-o", "Service.wsdl", serviceClass)
 *     .build();
 * </pre>
 *
 * @see <a href="https://github.com/spring-projects/spring-boot/blob/main/build-plugin/spring-boot-maven-plugin/src/main/java/org/springframework/boot/maven/CommandLineBuilder.java">
 *     Spring Boot CommandLineBuilder</a>
 */
public final class CommandLineBuilder {

    private final List<String> jvmArguments = new ArrayList<>();

    private final List<String> classpathEntries = new ArrayList<>();

    private final String mainClass;

    private final List<String> arguments = new ArrayList<>();

    private final Map<String, String> systemProperties = new LinkedHashMap<>();

    private CommandLineBuilder(String mainClass) {
        if (mainClass == null || mainClass.isEmpty()) {
            throw new IllegalArgumentException("mainClass must not be null or empty");
        }
        this.mainClass = mainClass;
    }

    /**
     * Creates a new {@link CommandLineBuilder} for the given main class.
     * @param mainClass the fully qualified main class name
     * @return a new builder instance
     */
    public static CommandLineBuilder forMainClass(String mainClass) {
        return new CommandLineBuilder(mainClass);
    }

    /**
     * Adds JVM arguments to the command line.
     * These are placed before the main class on the command line.
     * @param jvmArguments the JVM arguments (e.g. {@code -Xmx512m})
     * @return this builder
     */
    public CommandLineBuilder withJvmArguments(String... jvmArguments) {
        if (jvmArguments != null) {
            Arrays.stream(jvmArguments)
                    .filter(Objects::nonNull)
                    .forEach(this.jvmArguments::add);
        }
        return this;
    }

    /**
     * Adds system properties to the command line as {@code -Dkey=value} options.
     * These are placed before the main class on the command line.
     * @param systemProperties the system properties to add
     * @return this builder
     */
    public CommandLineBuilder withSystemProperties(Map<String, String> systemProperties) {
        if (systemProperties != null) {
            this.systemProperties.putAll(systemProperties);
        }
        return this;
    }

    /**
     * Sets the classpath using a {@link ClassPath} instance.
     * Replaces any previously configured classpath entries.
     * @param classPath the classpath
     * @return this builder
     */
    public CommandLineBuilder withClasspath(ClassPath classPath) {
        this.classpathEntries.clear();
        if (classPath != null && !classPath.isEmpty()) {
            this.classpathEntries.add(classPath.toString());
        }
        return this;
    }

    /**
     * Sets the classpath using individual path entries.
     * Replaces any previously configured classpath entries.
     * @param entries the classpath entries
     * @return this builder
     */
    public CommandLineBuilder withClasspathEntries(List<String> entries) {
        this.classpathEntries.clear();
        if (entries != null) {
            entries.stream()
                    .filter(e -> e != null && !e.isEmpty())
                    .forEach(this.classpathEntries::add);
        }
        return this;
    }

    /**
     * Adds program arguments to the command line.
     * These are placed after the main class on the command line.
     * @param arguments the program arguments
     * @return this builder
     */
    public CommandLineBuilder withArguments(String... arguments) {
        if (arguments != null) {
            Arrays.stream(arguments)
                    .filter(Objects::nonNull)
                    .forEach(this.arguments::add);
        }
        return this;
    }

    /**
     * Adds program arguments from a list.
     * @param arguments the program arguments
     * @return this builder
     */
    public CommandLineBuilder withArguments(List<String> arguments) {
        if (arguments != null) {
            arguments.stream()
                    .filter(Objects::nonNull)
                    .forEach(this.arguments::add);
        }
        return this;
    }

    /**
     * Returns the main class name.
     * @return the main class
     */
    public String getMainClass() {
        return mainClass;
    }

    /**
     * Builds the complete command-line arguments list.
     * <p>
     * The order of the resulting list is:
     * <ol>
     *   <li>JVM arguments</li>
     *   <li>System properties (as {@code -Dkey=value})</li>
     *   <li>Classpath ({@code -cp <path>})</li>
     *   <li>Main class</li>
     *   <li>Program arguments</li>
     * </ol>
     * @return an unmodifiable list of command-line arguments
     */
    public List<String> build() {
        List<String> commandLine = new ArrayList<>();

        // JVM arguments
        commandLine.addAll(this.jvmArguments);

        // System properties
        for (Map.Entry<String, String> entry : this.systemProperties.entrySet()) {
            String formatted = formatSystemProperty(entry.getKey(), entry.getValue());
            if (formatted != null && !formatted.isEmpty()) {
                commandLine.add(formatted);
            }
        }

        // Classpath
        if (!this.classpathEntries.isEmpty()) {
            ClassPath cp = ClassPath.of(this.classpathEntries);
            commandLine.addAll(cp.args("-cp"));
        }

        // Main class
        commandLine.add(this.mainClass);

        // Program arguments
        commandLine.addAll(this.arguments);

        return Collections.unmodifiableList(commandLine);
    }

    private static String formatSystemProperty(String key, String value) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (value != null && !value.isEmpty()) {
            return "-D" + key + "=" + value;
        }
        return "-D" + key;
    }

}
