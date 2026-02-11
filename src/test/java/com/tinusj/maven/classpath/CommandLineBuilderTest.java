package com.tinusj.maven.classpath;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLineBuilderTest {

    @Test
    public void testForMainClassCreatesBuilder() {
        CommandLineBuilder builder = CommandLineBuilder.forMainClass("com.example.Main");
        assertEquals("com.example.Main", builder.getMainClass());
    }

    @Test
    public void testForMainClassNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> CommandLineBuilder.forMainClass(null));
    }

    @Test
    public void testForMainClassEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> CommandLineBuilder.forMainClass(""));
    }

    @Test
    public void testBuildMinimalCommandLine() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main").build();
        assertEquals(1, cmd.size());
        assertEquals("com.example.Main", cmd.get(0));
    }

    @Test
    public void testBuildWithJvmArguments() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withJvmArguments("-Xmx512m", "-Xms256m")
                .build();
        assertEquals(3, cmd.size());
        assertEquals("-Xmx512m", cmd.get(0));
        assertEquals("-Xms256m", cmd.get(1));
        assertEquals("com.example.Main", cmd.get(2));
    }

    @Test
    public void testBuildWithNullJvmArguments() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withJvmArguments((String[]) null)
                .build();
        assertEquals(1, cmd.size());
    }

    @Test
    public void testBuildWithJvmArgumentsFiltersNulls() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withJvmArguments("-Xmx512m", null, "-Xms256m")
                .build();
        assertEquals(3, cmd.size());
        assertEquals("-Xmx512m", cmd.get(0));
        assertEquals("-Xms256m", cmd.get(1));
    }

    @Test
    public void testBuildWithSystemProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("java.io.tmpdir", "/tmp/work");
        props.put("debug.mode", "true");

        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withSystemProperties(props)
                .build();

        assertEquals(3, cmd.size());
        assertEquals("-Djava.io.tmpdir=/tmp/work", cmd.get(0));
        assertEquals("-Ddebug.mode=true", cmd.get(1));
        assertEquals("com.example.Main", cmd.get(2));
    }

    @Test
    public void testBuildWithSystemPropertyNoValue() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("enable.feature", null);

        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withSystemProperties(props)
                .build();

        assertEquals(2, cmd.size());
        assertEquals("-Denable.feature", cmd.get(0));
    }

    @Test
    public void testBuildWithNullSystemProperties() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withSystemProperties(null)
                .build();
        assertEquals(1, cmd.size());
    }

    @Test
    public void testBuildWithClasspath() {
        ClassPath cp = ClassPath.of("/lib/a.jar", "/lib/b.jar");

        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withClasspath(cp)
                .build();

        assertTrue(cmd.contains("-cp"));
        String sep = File.pathSeparator;
        assertTrue(cmd.contains("/lib/a.jar" + sep + "/lib/b.jar"));
        assertEquals("com.example.Main", cmd.get(cmd.size() - 1));
    }

    @Test
    public void testBuildWithEmptyClasspath() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withClasspath(ClassPath.empty())
                .build();
        assertFalse(cmd.contains("-cp"));
    }

    @Test
    public void testBuildWithNullClasspath() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withClasspath(null)
                .build();
        assertFalse(cmd.contains("-cp"));
    }

    @Test
    public void testBuildWithClasspathEntries() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withClasspathEntries(Arrays.asList("/lib/a.jar", "/lib/b.jar"))
                .build();

        assertTrue(cmd.contains("-cp"));
        assertEquals("com.example.Main", cmd.get(cmd.size() - 1));
    }

    @Test
    public void testBuildWithVarargArguments() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withArguments("-failOnError", "-war", "/path/to/war")
                .build();

        assertEquals(4, cmd.size());
        assertEquals("com.example.Main", cmd.get(0));
        assertEquals("-failOnError", cmd.get(1));
        assertEquals("-war", cmd.get(2));
        assertEquals("/path/to/war", cmd.get(3));
    }

    @Test
    public void testBuildWithListArguments() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withArguments(Arrays.asList("-wsdl", "-createxsdimports"))
                .build();

        assertEquals(3, cmd.size());
        assertEquals("com.example.Main", cmd.get(0));
        assertEquals("-wsdl", cmd.get(1));
        assertEquals("-createxsdimports", cmd.get(2));
    }

    @Test
    public void testBuildWithNullArguments() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withArguments((String[]) null)
                .build();
        assertEquals(1, cmd.size());
    }

    @Test
    public void testBuildWithArgumentsFiltersNulls() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withArguments("arg1", null, "arg2")
                .build();
        assertEquals(3, cmd.size());
        assertEquals("arg1", cmd.get(1));
        assertEquals("arg2", cmd.get(2));
    }

    @Test
    public void testBuildGwtCompilerCommandLine() {
        // Simulates the GWT compiler command from the issue
        Map<String, String> props = new LinkedHashMap<>();
        props.put("java.io.tmpdir", "/tmp/gwt");

        List<String> cmd = CommandLineBuilder.forMainClass("com.google.gwt.dev.Compiler")
                .withJvmArguments("-Xmx1g")
                .withSystemProperties(props)
                .withClasspath(ClassPath.of("/lib/gwt-dev.jar", "/lib/gwt-user.jar"))
                .withArguments(
                        "-failOnError",
                        "-XmethodNameDisplayMode", "FULL",
                        "-war", "/project/GWT/war",
                        "-strict",
                        "-style", "OBF",
                        "-logLevel", "INFO",
                        "-localWorkers", "4",
                        "com.example.MyModule"
                )
                .build();

        // Verify order: JVM args, system props, classpath, main class, arguments
        assertEquals("-Xmx1g", cmd.get(0));
        assertEquals("-Djava.io.tmpdir=/tmp/gwt", cmd.get(1));
        assertTrue(cmd.contains("-cp"));
        assertTrue(cmd.contains("com.google.gwt.dev.Compiler"));
        assertTrue(cmd.contains("-failOnError"));
        assertTrue(cmd.contains("com.example.MyModule"));

        // Main class should come before program arguments
        int mainClassIdx = cmd.indexOf("com.google.gwt.dev.Compiler");
        int failOnErrorIdx = cmd.indexOf("-failOnError");
        assertTrue(mainClassIdx < failOnErrorIdx);
    }

    @Test
    public void testBuildCxfWsdlCommandLine() {
        // Simulates the CXF WSDL generation command from the issue
        List<String> cmd = CommandLineBuilder.forMainClass("org.apache.cxf.tools.java2ws.JavaToWS")
                .withClasspath(ClassPath.of("/lib/cxf.jar", "/project/build"))
                .withArguments(
                        "-wsdl",
                        "-createxsdimports",
                        "-d", "/project/war/WEB-INF/wsdl",
                        "-o", "Offsite.wsdl",
                        "com.example.OffsiteImpl"
                )
                .build();

        assertTrue(cmd.contains("-cp"));
        assertTrue(cmd.contains("org.apache.cxf.tools.java2ws.JavaToWS"));
        assertTrue(cmd.contains("-wsdl"));
        assertTrue(cmd.contains("com.example.OffsiteImpl"));
    }

    @Test
    public void testBuildResultIsUnmodifiable() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main").build();
        assertThrows(UnsupportedOperationException.class, () -> cmd.add("extra"));
    }

    @Test
    public void testBuildFullCommandLineOrdering() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("key", "value");

        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withJvmArguments("-Xmx256m")
                .withSystemProperties(props)
                .withClasspath(ClassPath.of("/some/path"))
                .withArguments("arg1")
                .build();

        // Order: jvm args, system props, -cp, classpath, main class, program args
        assertEquals("-Xmx256m", cmd.get(0));
        assertEquals("-Dkey=value", cmd.get(1));
        assertEquals("-cp", cmd.get(2));
        assertEquals("/some/path", cmd.get(3));
        assertEquals("com.example.Main", cmd.get(4));
        assertEquals("arg1", cmd.get(5));
    }

    @Test
    public void testMultipleWithArgumentsCalls() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withArguments("-first")
                .withArguments("-second")
                .build();

        assertEquals(3, cmd.size());
        assertEquals("-first", cmd.get(1));
        assertEquals("-second", cmd.get(2));
    }

    @Test
    public void testMultipleWithJvmArgumentsCalls() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withJvmArguments("-Xmx256m")
                .withJvmArguments("-Xms128m")
                .build();

        assertEquals(3, cmd.size());
        assertEquals("-Xmx256m", cmd.get(0));
        assertEquals("-Xms128m", cmd.get(1));
    }

    @Test
    public void testWithClasspathEntriesFiltersNullsAndEmpty() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withClasspathEntries(Arrays.asList("/valid", null, "", "/also-valid"))
                .build();

        assertTrue(cmd.contains("-cp"));
        int cpIdx = cmd.indexOf("-cp");
        String cpValue = cmd.get(cpIdx + 1);
        assertTrue(cpValue.contains("/valid"));
        assertTrue(cpValue.contains("/also-valid"));
    }

    @Test
    public void testWithEmptyListArguments() {
        List<String> cmd = CommandLineBuilder.forMainClass("com.example.Main")
                .withArguments(Collections.emptyList())
                .build();
        assertEquals(1, cmd.size());
    }
}
