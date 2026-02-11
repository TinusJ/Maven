package com.tinusj.maven;

import com.tinusj.maven.support.ClassPath;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaProcessMojoTest {

    @TempDir
    File tempFolder;

    private JavaProcessMojo mojo;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new JavaProcessMojo();

        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setOutputDirectory(new File(tempFolder, "classes").getAbsolutePath());
        project.setBuild(build);

        setField(mojo, "project", project);
        setField(mojo, "mainClass", "com.example.Main");
        setField(mojo, "failOnError", true);
        setField(mojo, "skip", false);
        setField(mojo, "includeProjectClasspath", false);
        setField(mojo, "workingDirectory", tempFolder);
    }

    @Test
    public void testBuildCommandMinimal() throws Exception {
        List<String> cmd = mojo.buildCommand();

        // Should contain: java, mainClass
        assertTrue(cmd.size() >= 2);
        assertTrue(cmd.get(0).contains("java"));
        assertEquals("com.example.Main", cmd.get(cmd.size() - 1));
    }

    @Test
    public void testBuildCommandWithArguments() throws Exception {
        setField(mojo, "arguments", Arrays.asList("-failOnError", "-war", "/path/to/war"));

        List<String> cmd = mojo.buildCommand();

        assertTrue(cmd.contains("-failOnError"));
        assertTrue(cmd.contains("-war"));
        assertTrue(cmd.contains("/path/to/war"));
    }

    @Test
    public void testBuildCommandWithJvmArguments() throws Exception {
        setField(mojo, "jvmArguments", Arrays.asList("-Xmx512m", "-Xms256m"));

        List<String> cmd = mojo.buildCommand();

        assertTrue(cmd.contains("-Xmx512m"));
        assertTrue(cmd.contains("-Xms256m"));
        // JVM args should come before main class
        int xmxIdx = cmd.indexOf("-Xmx512m");
        int mainIdx = cmd.indexOf("com.example.Main");
        assertTrue(xmxIdx < mainIdx);
    }

    @Test
    public void testBuildCommandWithSystemProperties() throws Exception {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("java.io.tmpdir", "/tmp/work");
        setField(mojo, "systemProperties", props);

        List<String> cmd = mojo.buildCommand();

        assertTrue(cmd.contains("-Djava.io.tmpdir=/tmp/work"));
    }

    @Test
    public void testBuildCommandWithClasspathEntries() throws Exception {
        setField(mojo, "classpathEntries", Arrays.asList("/lib/a.jar", "/lib/b.jar"));

        List<String> cmd = mojo.buildCommand();

        assertTrue(cmd.contains("-cp"));
    }

    @Test
    public void testBuildCommandGwtStyle() throws Exception {
        // Simulate the GWT compiler command from the issue
        setField(mojo, "mainClass", "com.google.gwt.dev.Compiler");
        setField(mojo, "classpathEntries", Arrays.asList("/lib/gwt-dev.jar", "/lib/gwt-user.jar"));
        setField(mojo, "jvmArguments", Arrays.asList("-Djava.io.tmpdir=/tmp/gwt"));
        setField(mojo, "arguments", Arrays.asList(
                "-failOnError",
                "-XmethodNameDisplayMode", "FULL",
                "-war", "/project/GWT/war",
                "-strict",
                "-style", "OBF",
                "-logLevel", "INFO",
                "-localWorkers", "4",
                "com.example.MyModule"
        ));

        List<String> cmd = mojo.buildCommand();

        assertTrue(cmd.contains("com.google.gwt.dev.Compiler"));
        assertTrue(cmd.contains("-failOnError"));
        assertTrue(cmd.contains("-Djava.io.tmpdir=/tmp/gwt"));
        assertTrue(cmd.contains("com.example.MyModule"));
    }

    @Test
    public void testBuildCommandCxfStyle() throws Exception {
        // Simulate the CXF WSDL generation command from the issue
        setField(mojo, "mainClass", "org.apache.cxf.tools.java2ws.JavaToWS");
        setField(mojo, "classpathEntries", Arrays.asList("/lib/cxf.jar"));
        setField(mojo, "arguments", Arrays.asList(
                "-wsdl",
                "-createxsdimports",
                "-d", "/project/war/WEB-INF/wsdl",
                "-o", "Offsite.wsdl",
                "com.example.OffsiteImpl"
        ));

        List<String> cmd = mojo.buildCommand();

        assertTrue(cmd.contains("org.apache.cxf.tools.java2ws.JavaToWS"));
        assertTrue(cmd.contains("-wsdl"));
        assertTrue(cmd.contains("com.example.OffsiteImpl"));
    }

    @Test
    public void testBuildClasspathWithEntries() throws Exception {
        setField(mojo, "classpathEntries", Arrays.asList("/lib/a.jar", "/lib/b.jar"));

        ClassPath cp = mojo.buildClasspath();

        assertFalse(cp.isEmpty());
        String sep = File.pathSeparator;
        assertEquals("/lib/a.jar" + sep + "/lib/b.jar", cp.toString());
    }

    @Test
    public void testBuildClasspathNoEntries() throws Exception {
        ClassPath cp = mojo.buildClasspath();
        assertTrue(cp.isEmpty());
    }

    @Test
    public void testExecuteSkip() throws Exception {
        setField(mojo, "skip", true);
        // Should not throw
        mojo.execute();
    }

    @Test
    public void testExecuteNullMainClass() throws Exception {
        setField(mojo, "mainClass", null);
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    public void testExecuteEmptyMainClass() throws Exception {
        setField(mojo, "mainClass", "");
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    public void testGetJavaExecutable() throws Exception {
        String javaExe = mojo.getJavaExecutable();
        assertTrue(javaExe.contains("java"));
    }

    @Test
    public void testExecuteSuccessfulProcess() throws Exception {
        // Use java -version as a simple process that should succeed
        setField(mojo, "mainClass", "-version");
        setField(mojo, "jvmArguments", null);
        setField(mojo, "arguments", null);
        setField(mojo, "classpathEntries", null);
        setField(mojo, "includeProjectClasspath", false);

        // This runs "java -version" which should succeed
        // We need to build a direct command for this
        // Instead, test the executeProcess method directly
        String javaExe = mojo.getJavaExecutable();
        int exitCode = mojo.executeProcess(Arrays.asList(javaExe, "-version"));
        assertEquals(0, exitCode);
    }

    @Test
    public void testBuildCommandOrderingComplete() throws Exception {
        setField(mojo, "jvmArguments", Arrays.asList("-Xmx256m"));
        Map<String, String> props = new LinkedHashMap<>();
        props.put("key", "value");
        setField(mojo, "systemProperties", props);
        setField(mojo, "classpathEntries", Arrays.asList("/lib/test.jar"));
        setField(mojo, "arguments", Arrays.asList("arg1"));

        List<String> cmd = mojo.buildCommand();

        // java executable first
        assertTrue(cmd.get(0).contains("java"));

        // Then JVM args before main class
        int jvmIdx = cmd.indexOf("-Xmx256m");
        int mainIdx = cmd.indexOf("com.example.Main");
        int argIdx = cmd.indexOf("arg1");

        assertTrue(jvmIdx > 0); // after java exe
        assertTrue(jvmIdx < mainIdx); // before main class
        assertTrue(mainIdx < argIdx); // main class before program args
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
