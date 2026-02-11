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

public class AnnotationProcessorMojoTest {

    @TempDir
    File tempFolder;

    private AnnotationProcessorMojo mojo;
    private File sourceDir;
    private File generatedSourcesDir;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new AnnotationProcessorMojo();

        sourceDir = new File(tempFolder, "src");
        sourceDir.mkdirs();
        generatedSourcesDir = new File(tempFolder, "generated-sources");

        File outputDir = new File(tempFolder, "classes");
        outputDir.mkdirs();

        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(sourceDir.getAbsolutePath());
        Build build = new Build();
        build.setOutputDirectory(outputDir.getAbsolutePath());
        project.setBuild(build);

        setField(mojo, "project", project);
        setField(mojo, "source", "17");
        setField(mojo, "target", "17");
        setField(mojo, "generatedSourcesDirectory", generatedSourcesDir);
        setField(mojo, "showWarnings", false);
        setField(mojo, "skip", false);
    }

    @Test
    public void testBuildOptionsBasic() throws Exception {
        List<String> options = mojo.buildOptions(
                ClassPath.of("/some/classpath.jar"), ClassPath.empty());

        assertTrue(options.contains("-source"));
        assertTrue(options.contains("17"));
        assertTrue(options.contains("-target"));
        assertTrue(options.contains("-proc:only"));
        assertTrue(options.contains("-s"));
        assertTrue(options.contains(generatedSourcesDir.getAbsolutePath()));
        assertTrue(options.contains("-classpath"));
        assertTrue(options.contains("/some/classpath.jar"));
        assertTrue(options.contains("-nowarn"));
    }

    @Test
    public void testBuildOptionsWithProcessorPath() throws Exception {
        setField(mojo, "processorPathEntries",
                Arrays.asList("/lib/processor.jar", "/lib/processor2.jar"));

        ClassPath processorPath = mojo.buildProcessorPath();
        List<String> options = mojo.buildOptions(ClassPath.empty(), processorPath);

        assertTrue(options.contains("-processorpath"));
    }

    @Test
    public void testBuildOptionsWithProcessors() throws Exception {
        setField(mojo, "annotationProcessors",
                Arrays.asList("com.example.Processor1", "com.example.Processor2"));

        List<String> options = mojo.buildOptions(ClassPath.empty(), ClassPath.empty());

        assertTrue(options.contains("-processor"));
        assertTrue(options.contains("com.example.Processor1,com.example.Processor2"));
    }

    @Test
    public void testBuildOptionsWithCompilerArguments() throws Exception {
        setField(mojo, "compilerArguments", Arrays.asList("-Akey=value", "-verbose"));

        List<String> options = mojo.buildOptions(ClassPath.empty(), ClassPath.empty());

        assertTrue(options.contains("-Akey=value"));
        assertTrue(options.contains("-verbose"));
    }

    @Test
    public void testBuildOptionsNullSourceTarget() throws Exception {
        setField(mojo, "source", null);
        setField(mojo, "target", null);

        List<String> options = mojo.buildOptions(ClassPath.empty(), ClassPath.empty());

        assertFalse(options.contains("-source"));
        assertFalse(options.contains("-target"));
        assertTrue(options.contains("-proc:only"));
    }

    @Test
    public void testBuildOptionsWithWarnings() throws Exception {
        setField(mojo, "showWarnings", true);

        List<String> options = mojo.buildOptions(ClassPath.empty(), ClassPath.empty());

        assertFalse(options.contains("-nowarn"));
    }

    @Test
    public void testBuildProcessorPathEmpty() throws Exception {
        ClassPath processorPath = mojo.buildProcessorPath();
        assertTrue(processorPath.isEmpty());
    }

    @Test
    public void testBuildProcessorPathWithEntries() throws Exception {
        setField(mojo, "processorPathEntries",
                Arrays.asList("/lib/processor.jar"));

        ClassPath processorPath = mojo.buildProcessorPath();
        assertFalse(processorPath.isEmpty());
        assertEquals("/lib/processor.jar", processorPath.toString());
    }

    @Test
    public void testCollectJavaFiles() throws Exception {
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
    public void testCollectJavaFilesNonExistentDir() throws Exception {
        List<File> files = mojo.collectJavaFiles(Arrays.asList("/nonexistent/path"));
        assertTrue(files.isEmpty());
    }

    @Test
    public void testExecuteSkip() throws Exception {
        setField(mojo, "skip", true);
        // Should not throw
        mojo.execute();
    }

    @Test
    public void testExecuteNoSourceFiles() throws Exception {
        // Empty source directory - should skip without error
        mojo.execute();
    }

    @Test
    public void testExecuteWithSourceFiles() throws Exception {
        // Create a simple Java file (no annotation processor, so proc:only should succeed
        // even though no sources are generated)
        createFile(new File(sourceDir, "Hello.java"),
                "public class Hello {\n    public static void main(String[] args) {}\n}");

        mojo.execute();

        // Generated sources directory should exist (created by the mojo)
        assertTrue(generatedSourcesDir.exists());
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
