package com.tinusj.maven;

import com.tinusj.maven.support.ClassPath;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCompilerMojoTest {

    @TempDir
    File tempFolder;

    private TestCompilerMojo mojo;
    private File testSourceDir;
    private File testOutputDir;
    private File mainOutputDir;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new TestCompilerMojo();

        testSourceDir = new File(tempFolder, "test-src");
        testSourceDir.mkdirs();
        testOutputDir = new File(tempFolder, "test-classes");
        testOutputDir.mkdirs();
        mainOutputDir = new File(tempFolder, "classes");
        mainOutputDir.mkdirs();

        MavenProject project = new MavenProject();
        project.addTestCompileSourceRoot(testSourceDir.getAbsolutePath());
        Build build = new Build();
        build.setOutputDirectory(mainOutputDir.getAbsolutePath());
        build.setTestOutputDirectory(testOutputDir.getAbsolutePath());
        project.setBuild(build);

        setField(mojo, "project", project);
        setField(mojo, "source", "1.8");
        setField(mojo, "target", "1.8");
        setField(mojo, "showWarnings", false);
        setField(mojo, "showDeprecation", false);
        setField(mojo, "skip", false);
        setField(mojo, "mavenTestSkip", false);
    }

    @Test
    public void testBuildJavacOptions() throws Exception {
        List<String> options = mojo.buildJavacOptions(
                ClassPath.of("/some/classpath.jar"), testOutputDir);

        assertTrue(options.contains("-source"));
        assertTrue(options.contains("1.8"));
        assertTrue(options.contains("-target"));
        assertTrue(options.contains("-d"));
        assertTrue(options.contains(testOutputDir.getAbsolutePath()));
        assertTrue(options.contains("-classpath"));
        assertTrue(options.contains("/some/classpath.jar"));
        assertTrue(options.contains("-nowarn"));
        assertFalse(options.contains("-deprecation"));
    }

    @Test
    public void testBuildJavacOptionsWithWarnings() throws Exception {
        setField(mojo, "showWarnings", true);
        setField(mojo, "showDeprecation", true);

        List<String> options = mojo.buildJavacOptions(
                ClassPath.of("/some/classpath.jar"), testOutputDir);

        assertFalse(options.contains("-nowarn"));
        assertTrue(options.contains("-deprecation"));
    }

    @Test
    public void testBuildJavacOptionsWithCompilerArguments() throws Exception {
        setField(mojo, "compilerArguments", Arrays.asList("-Xlint:all", "-verbose"));

        List<String> options = mojo.buildJavacOptions(
                ClassPath.of("/some/classpath.jar"), testOutputDir);

        assertTrue(options.contains("-Xlint:all"));
        assertTrue(options.contains("-verbose"));
    }

    @Test
    public void testBuildJavacOptionsEmptyClasspath() throws Exception {
        List<String> options = mojo.buildJavacOptions(ClassPath.empty(), testOutputDir);
        assertFalse(options.contains("-classpath"));
    }

    @Test
    public void testBuildJavacOptionsNullSourceTarget() throws Exception {
        setField(mojo, "source", null);
        setField(mojo, "target", null);

        List<String> options = mojo.buildJavacOptions(ClassPath.of("/cp"), testOutputDir);
        assertFalse(options.contains("-source"));
        assertFalse(options.contains("-target"));
    }

    @Test
    public void testCollectJavaFiles() throws Exception {
        createFile(new File(testSourceDir, "HelloTest.java"), "public class HelloTest {}");
        createFile(new File(testSourceDir, "readme.txt"), "not a java file");

        File subDir = new File(testSourceDir, "sub");
        subDir.mkdirs();
        createFile(new File(subDir, "WorldTest.java"), "public class WorldTest {}");

        List<File> files = mojo.collectJavaFiles(Arrays.asList(testSourceDir.getAbsolutePath()));
        assertEquals(2, files.size());
    }

    @Test
    public void testCollectJavaFilesEmpty() throws Exception {
        List<File> files = mojo.collectJavaFiles(Arrays.asList(testSourceDir.getAbsolutePath()));
        assertTrue(files.isEmpty());
    }

    @Test
    public void testValidateDirectories() throws Exception {
        File existingDir = new File(tempFolder, "existing");
        existingDir.mkdirs();

        List<String> result = mojo.validateDirectories(
                Arrays.asList(existingDir.getAbsolutePath(), "/nonexistent/dir"));
        assertEquals(1, result.size());
        assertEquals(existingDir.getAbsolutePath(), result.get(0));
    }

    @Test
    public void testValidateDirectoriesNull() throws Exception {
        List<String> result = mojo.validateDirectories(null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExecuteSkip() throws Exception {
        setField(mojo, "skip", true);
        // Should not throw
        mojo.execute();
    }

    @Test
    public void testExecuteMavenTestSkip() throws Exception {
        setField(mojo, "mavenTestSkip", true);
        // Should not throw
        mojo.execute();
    }

    @Test
    public void testExecuteNoSourceFiles() throws Exception {
        // Empty test source directory - should skip without error
        mojo.execute();
    }

    @Test
    public void testExecuteCompileTestSources() throws Exception {
        createFile(new File(testSourceDir, "HelloTest.java"),
                "public class HelloTest {\n"
                        + "    public void testHello() {\n"
                        + "        System.out.println(\"Hello\");\n"
                        + "    }\n"
                        + "}");

        mojo.execute();

        File classFile = new File(testOutputDir, "HelloTest.class");
        assertTrue(classFile.exists(), "Test class file should exist after compilation");
    }

    @Test
    public void testExecuteCompileMultipleTestSources() throws Exception {
        createFile(new File(testSourceDir, "FooTest.java"),
                "public class FooTest { public int getValue() { return 42; } }");
        createFile(new File(testSourceDir, "BarTest.java"),
                "public class BarTest { public FooTest getFoo() { return new FooTest(); } }");

        mojo.execute();

        assertTrue(new File(testOutputDir, "FooTest.class").exists());
        assertTrue(new File(testOutputDir, "BarTest.class").exists());
    }

    private void createFile(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
