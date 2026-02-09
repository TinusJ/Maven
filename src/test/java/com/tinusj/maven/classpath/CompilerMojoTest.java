package com.tinusj.maven.classpath;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class CompilerMojoTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CompilerMojo mojo;
    private File sourceDir;
    private File outputDir;

    @Before
    public void setUp() throws Exception {
        mojo = new CompilerMojo();

        // Create source and output directories
        sourceDir = tempFolder.newFolder("src");
        outputDir = tempFolder.newFolder("classes");

        // Set up a mock project
        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(sourceDir.getAbsolutePath());
        setField(mojo, "project", project);
        setField(mojo, "compiler", "javac");
        setField(mojo, "source", "1.8");
        setField(mojo, "target", "1.8");
        setField(mojo, "outputDirectory", outputDir);
        setField(mojo, "showWarnings", false);
        setField(mojo, "showDeprecation", false);
    }

    @Test
    public void testGetSourceDirectoriesFromProject() throws Exception {
        List<String> dirs = mojo.getSourceDirectories();
        assertEquals(1, dirs.size());
        assertEquals(sourceDir.getAbsolutePath(), dirs.get(0));
    }

    @Test
    public void testGetSourceDirectoriesCustom() throws Exception {
        File customDir = tempFolder.newFolder("custom-src");
        setField(mojo, "sourceDirectories", Arrays.asList(customDir.getAbsolutePath()));

        List<String> dirs = mojo.getSourceDirectories();
        assertEquals(1, dirs.size());
        assertEquals(customDir.getAbsolutePath(), dirs.get(0));
    }

    @Test
    public void testGetSourceDirectoriesNonExistent() throws Exception {
        setField(mojo, "sourceDirectories", Arrays.asList("/nonexistent/path"));

        List<String> dirs = mojo.getSourceDirectories();
        assertTrue(dirs.isEmpty());
    }

    @Test
    public void testCollectJavaFiles() throws Exception {
        // Create some java files
        createFile(new File(sourceDir, "Hello.java"), "public class Hello {}");
        createFile(new File(sourceDir, "readme.txt"), "not a java file");

        File subDir = new File(sourceDir, "sub");
        subDir.mkdirs();
        createFile(new File(subDir, "World.java"), "public class World {}");

        List<File> files = mojo.collectJavaFiles(Arrays.asList(sourceDir.getAbsolutePath()));
        assertEquals(2, files.size());
    }

    @Test
    public void testCollectJavaFilesEmpty() throws Exception {
        List<File> files = mojo.collectJavaFiles(Arrays.asList(sourceDir.getAbsolutePath()));
        assertTrue(files.isEmpty());
    }

    @Test
    public void testBuildJavacOptions() throws Exception {
        List<String> options = mojo.buildJavacOptions("/some/classpath.jar");

        assertTrue(options.contains("-source"));
        assertTrue(options.contains("1.8"));
        assertTrue(options.contains("-target"));
        assertTrue(options.contains("-d"));
        assertTrue(options.contains(outputDir.getAbsolutePath()));
        assertTrue(options.contains("-classpath"));
        assertTrue(options.contains("/some/classpath.jar"));
        assertTrue(options.contains("-nowarn"));
        assertFalse(options.contains("-deprecation"));
    }

    @Test
    public void testBuildJavacOptionsWithWarnings() throws Exception {
        setField(mojo, "showWarnings", true);
        setField(mojo, "showDeprecation", true);

        List<String> options = mojo.buildJavacOptions("/some/classpath.jar");
        assertFalse(options.contains("-nowarn"));
        assertTrue(options.contains("-deprecation"));
    }

    @Test
    public void testBuildJavacOptionsWithCompilerArguments() throws Exception {
        setField(mojo, "compilerArguments", Arrays.asList("-Xlint:all", "-verbose"));

        List<String> options = mojo.buildJavacOptions("/some/classpath.jar");
        assertTrue(options.contains("-Xlint:all"));
        assertTrue(options.contains("-verbose"));
    }

    @Test
    public void testBuildEcjArguments() throws Exception {
        File propsFile = tempFolder.newFile("ecj.properties");
        setField(mojo, "propertiesFile", propsFile);

        List<File> sourceFiles = Arrays.asList(
                new File(sourceDir, "Hello.java"),
                new File(sourceDir, "World.java"));

        List<String> args = mojo.buildEcjArguments(sourceFiles, "/some/classpath.jar");

        assertTrue(args.contains("-source"));
        assertTrue(args.contains("1.8"));
        assertTrue(args.contains("-target"));
        assertTrue(args.contains("-d"));
        assertTrue(args.contains(outputDir.getAbsolutePath()));
        assertTrue(args.contains("-classpath"));
        assertTrue(args.contains("/some/classpath.jar"));
        assertTrue(args.contains("-properties"));
        assertTrue(args.contains(propsFile.getAbsolutePath()));
        assertTrue(args.contains("-nowarn"));
        // Source files should be at the end
        assertTrue(args.contains(new File(sourceDir, "Hello.java").getAbsolutePath()));
        assertTrue(args.contains(new File(sourceDir, "World.java").getAbsolutePath()));
    }

    @Test
    public void testBuildEcjArgumentsNoProperties() throws Exception {
        List<File> sourceFiles = Arrays.asList(new File(sourceDir, "Hello.java"));
        List<String> args = mojo.buildEcjArguments(sourceFiles, "/some/classpath.jar");

        assertFalse(args.contains("-properties"));
    }

    @Test
    public void testBuildEcjArgumentsNonExistentProperties() throws Exception {
        setField(mojo, "propertiesFile", new File("/nonexistent/file.properties"));

        List<File> sourceFiles = Arrays.asList(new File(sourceDir, "Hello.java"));
        List<String> args = mojo.buildEcjArguments(sourceFiles, "/some/classpath.jar");

        // Should not include -properties for non-existent file
        assertFalse(args.contains("-properties"));
    }

    @Test
    public void testExecuteNoSourceFiles() throws Exception {
        // Empty source directory - should skip without error
        mojo.execute();
    }

    @Test
    public void testExecuteCompileWithJavac() throws Exception {
        // Create a simple Java source file
        createFile(new File(sourceDir, "Hello.java"),
                "public class Hello {\n    public static void main(String[] args) {}\n}");

        mojo.execute();

        // Verify the class file was created
        File classFile = new File(outputDir, "Hello.class");
        assertTrue("Class file should exist after compilation", classFile.exists());
    }

    @Test(expected = MojoExecutionException.class)
    public void testExecuteUnsupportedCompiler() throws Exception {
        setField(mojo, "compiler", "unsupported");
        createFile(new File(sourceDir, "Hello.java"), "public class Hello {}");
        mojo.execute();
    }

    @Test(expected = MojoExecutionException.class)
    public void testLoadEcjMainClassNotFound() throws Exception {
        mojo.loadEcjMainClass();
    }

    @Test
    public void testExecuteCompileWithJavacMultipleFiles() throws Exception {
        createFile(new File(sourceDir, "Foo.java"),
                "public class Foo { public int getValue() { return 42; } }");
        createFile(new File(sourceDir, "Bar.java"),
                "public class Bar { public Foo getFoo() { return new Foo(); } }");

        mojo.execute();

        assertTrue(new File(outputDir, "Foo.class").exists());
        assertTrue(new File(outputDir, "Bar.class").exists());
    }

    @Test
    public void testBuildJavacOptionsEmptyClasspath() throws Exception {
        List<String> options = mojo.buildJavacOptions("");
        assertFalse(options.contains("-classpath"));
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
