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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompilerMojoTest {

    @TempDir
    File tempFolder;

    private CompilerMojo mojo;
    private File sourceDir;
    private File outputDir;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new CompilerMojo();

        // Create source and output directories
        sourceDir = new File(tempFolder, "src");
        sourceDir.mkdirs();
        outputDir = new File(tempFolder, "classes");
        outputDir.mkdirs();

        // Set up a mock project with maven.compiler.source/target properties
        MavenProject project = new MavenProject();
        project.addCompileSourceRoot(sourceDir.getAbsolutePath());
        Build build = new Build();
        build.setOutputDirectory(outputDir.getAbsolutePath());
        project.setBuild(build);
        project.getProperties().setProperty("maven.compiler.source", "1.8");
        project.getProperties().setProperty("maven.compiler.target", "1.8");

        setField(mojo, "project", project);
        setField(mojo, "compiler", "javac");
        setField(mojo, "source", "1.8");
        setField(mojo, "target", "1.8");
        setField(mojo, "showWarnings", false);
        setField(mojo, "showDeprecation", false);
    }

    @Test
    public void testResolveModulesDefaultsToProject() throws Exception {
        List<CompilerMojo.CompilerModule> resolved = mojo.resolveModules();
        assertEquals(1, resolved.size());
        assertEquals(outputDir.getAbsolutePath(),
                resolved.get(0).getOutputDirectory().getAbsolutePath());
    }

    @Test
    public void testResolveModulesExplicit() throws Exception {
        File srcA = new File(tempFolder, "module-a-src");
        srcA.mkdirs();
        File outA = new File(tempFolder, "module-a-out");
        outA.mkdirs();
        File srcB = new File(tempFolder, "module-b-src");
        srcB.mkdirs();
        File outB = new File(tempFolder, "module-b-out");
        outB.mkdirs();

        CompilerMojo.CompilerModule modA = new CompilerMojo.CompilerModule();
        modA.setSourceDirectories(Arrays.asList(srcA.getAbsolutePath()));
        modA.setOutputDirectory(outA);

        CompilerMojo.CompilerModule modB = new CompilerMojo.CompilerModule();
        modB.setSourceDirectories(Arrays.asList(srcB.getAbsolutePath()));
        modB.setOutputDirectory(outB);

        setField(mojo, "modules", Arrays.asList(modA, modB));

        List<CompilerMojo.CompilerModule> resolved = mojo.resolveModules();
        assertEquals(2, resolved.size());
    }

    @Test
    public void testResolveModulesSkipsInvalidSourceDirs() throws Exception {
        CompilerMojo.CompilerModule mod = new CompilerMojo.CompilerModule();
        mod.setSourceDirectories(Arrays.asList("/nonexistent/path"));
        mod.setOutputDirectory(outputDir);

        setField(mojo, "modules", Arrays.asList(mod));

        List<CompilerMojo.CompilerModule> resolved = mojo.resolveModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testCollectAllSourceDirectories() throws Exception {
        File srcA = new File(tempFolder, "mod-a");
        srcA.mkdirs();
        File srcB = new File(tempFolder, "mod-b");
        srcB.mkdirs();

        CompilerMojo.CompilerModule modA = new CompilerMojo.CompilerModule();
        modA.setSourceDirectories(Arrays.asList(srcA.getAbsolutePath()));
        modA.setOutputDirectory(outputDir);

        CompilerMojo.CompilerModule modB = new CompilerMojo.CompilerModule();
        modB.setSourceDirectories(Arrays.asList(srcB.getAbsolutePath()));
        modB.setOutputDirectory(outputDir);

        List<String> allDirs = mojo.collectAllSourceDirectories(Arrays.asList(modA, modB));
        assertEquals(2, allDirs.size());
        assertTrue(allDirs.contains(srcA.getAbsolutePath()));
        assertTrue(allDirs.contains(srcB.getAbsolutePath()));
    }

    @Test
    public void testCollectAllSourceDirectoriesNoDuplicates() throws Exception {
        CompilerMojo.CompilerModule modA = new CompilerMojo.CompilerModule();
        modA.setSourceDirectories(Arrays.asList(sourceDir.getAbsolutePath()));
        modA.setOutputDirectory(outputDir);

        CompilerMojo.CompilerModule modB = new CompilerMojo.CompilerModule();
        modB.setSourceDirectories(Arrays.asList(sourceDir.getAbsolutePath()));
        modB.setOutputDirectory(outputDir);

        List<String> allDirs = mojo.collectAllSourceDirectories(Arrays.asList(modA, modB));
        assertEquals(1, allDirs.size());
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
    public void testBuildJavacOptions() throws Exception {
        List<String> options = mojo.buildJavacOptions(
                ClassPath.of("/some/classpath.jar"), ClassPath.of("/some/sourcepath"), outputDir);

        assertTrue(options.contains("-source"));
        assertTrue(options.contains("1.8"));
        assertTrue(options.contains("-target"));
        assertTrue(options.contains("-d"));
        assertTrue(options.contains(outputDir.getAbsolutePath()));
        assertTrue(options.contains("-classpath"));
        assertTrue(options.contains("/some/classpath.jar"));
        assertTrue(options.contains("-sourcepath"));
        assertTrue(options.contains("/some/sourcepath"));
        assertTrue(options.contains("-nowarn"));
        assertFalse(options.contains("-deprecation"));
    }

    @Test
    public void testBuildJavacOptionsWithWarnings() throws Exception {
        setField(mojo, "showWarnings", true);
        setField(mojo, "showDeprecation", true);

        List<String> options = mojo.buildJavacOptions(ClassPath.of("/some/classpath.jar"), ClassPath.empty(), outputDir);
        assertFalse(options.contains("-nowarn"));
        assertTrue(options.contains("-deprecation"));
    }

    @Test
    public void testBuildJavacOptionsWithCompilerArguments() throws Exception {
        setField(mojo, "compilerArguments", Arrays.asList("-Xlint:all", "-verbose"));

        List<String> options = mojo.buildJavacOptions(ClassPath.of("/some/classpath.jar"), ClassPath.empty(), outputDir);
        assertTrue(options.contains("-Xlint:all"));
        assertTrue(options.contains("-verbose"));
    }

    @Test
    public void testBuildEcjArguments() throws Exception {
        File propsFile = new File(tempFolder, "ecj.properties");
        propsFile.createNewFile();
        setField(mojo, "propertiesFile", propsFile);

        List<File> sourceFiles = Arrays.asList(
                new File(sourceDir, "Hello.java"),
                new File(sourceDir, "World.java"));

        List<String> args = mojo.buildEcjArguments(sourceFiles, ClassPath.of("/some/classpath.jar"),
                ClassPath.of("/some/sourcepath"), outputDir);

        assertTrue(args.contains("-source"));
        assertTrue(args.contains("1.8"));
        assertTrue(args.contains("-target"));
        assertTrue(args.contains("-d"));
        assertTrue(args.contains(outputDir.getAbsolutePath()));
        assertTrue(args.contains("-classpath"));
        assertTrue(args.contains("/some/classpath.jar"));
        assertTrue(args.contains("-sourcepath"));
        assertTrue(args.contains("/some/sourcepath"));
        assertTrue(args.contains("-properties"));
        assertTrue(args.contains(propsFile.getAbsolutePath()));
        assertTrue(args.contains("-nowarn"));
        assertTrue(args.contains(new File(sourceDir, "Hello.java").getAbsolutePath()));
        assertTrue(args.contains(new File(sourceDir, "World.java").getAbsolutePath()));
    }

    @Test
    public void testBuildEcjArgumentsNoProperties() throws Exception {
        List<File> sourceFiles = Arrays.asList(new File(sourceDir, "Hello.java"));
        List<String> args = mojo.buildEcjArguments(sourceFiles, ClassPath.of("/some/classpath.jar"),
                ClassPath.empty(), outputDir);

        assertFalse(args.contains("-properties"));
    }

    @Test
    public void testBuildEcjArgumentsNonExistentProperties() throws Exception {
        setField(mojo, "propertiesFile", new File("/nonexistent/file.properties"));

        List<File> sourceFiles = Arrays.asList(new File(sourceDir, "Hello.java"));
        List<String> args = mojo.buildEcjArguments(sourceFiles, ClassPath.of("/some/classpath.jar"),
                ClassPath.empty(), outputDir);

        assertFalse(args.contains("-properties"));
    }

    @Test
    public void testExecuteNoSourceFiles() throws Exception {
        // Empty source directory - should skip without error
        mojo.execute();
    }

    @Test
    public void testExecuteCompileWithJavac() throws Exception {
        createFile(new File(sourceDir, "Hello.java"),
                "public class Hello {\n    public static void main(String[] args) {}\n}");

        mojo.execute();

        File classFile = new File(outputDir, "Hello.class");
        assertTrue(classFile.exists(), "Class file should exist after compilation");
    }

    @Test
    public void testExecuteUnsupportedCompiler() throws Exception {
        setField(mojo, "compiler", "unsupported");
        createFile(new File(sourceDir, "Hello.java"), "public class Hello {}");
        assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    public void testLoadEcjMainClassNotFound() throws Exception {
        assertThrows(MojoExecutionException.class, () -> mojo.loadEcjMainClass());
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
        List<String> options = mojo.buildJavacOptions(ClassPath.empty(), ClassPath.empty(), outputDir);
        assertFalse(options.contains("-classpath"));
    }

    @Test
    public void testBuildJavacOptionsNullSourceTarget() throws Exception {
        setField(mojo, "source", null);
        setField(mojo, "target", null);
        List<String> options = mojo.buildJavacOptions(ClassPath.of("/cp"), ClassPath.empty(), outputDir);
        assertFalse(options.contains("-source"));
        assertFalse(options.contains("-target"));
    }

    @Test
    public void testCompileMultiModuleWithJavac() throws Exception {
        // Module A has a class that Module B depends on
        File srcA = new File(tempFolder, "module-a-src");
        srcA.mkdirs();
        File outA = new File(tempFolder, "module-a-classes");
        outA.mkdirs();
        File srcB = new File(tempFolder, "module-b-src");
        srcB.mkdirs();
        File outB = new File(tempFolder, "module-b-classes");
        outB.mkdirs();

        createFile(new File(srcA, "Shared.java"),
                "public class Shared { public int value() { return 1; } }");
        createFile(new File(srcB, "Consumer.java"),
                "public class Consumer { public Shared get() { return new Shared(); } }");

        CompilerMojo.CompilerModule modA = new CompilerMojo.CompilerModule();
        modA.setSourceDirectories(Arrays.asList(srcA.getAbsolutePath()));
        modA.setOutputDirectory(outA);

        CompilerMojo.CompilerModule modB = new CompilerMojo.CompilerModule();
        modB.setSourceDirectories(Arrays.asList(srcB.getAbsolutePath()));
        modB.setOutputDirectory(outB);

        setField(mojo, "modules", Arrays.asList(modA, modB));

        mojo.execute();

        // Module A output should have Shared.class
        assertTrue(new File(outA, "Shared.class").exists(),
                "Shared.class should be in module A output");
        // Module B output should have Consumer.class
        assertTrue(new File(outB, "Consumer.class").exists(),
                "Consumer.class should be in module B output");
        // Cross-check: module A should NOT have Consumer.class
        assertFalse(new File(outA, "Consumer.class").exists(),
                "Consumer.class should NOT be in module A output");
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
    public void testBuildSourcepath() throws Exception {
        List<String> dirs = Arrays.asList("/path/a", "/path/b");
        ClassPath sourcepath = mojo.buildSourcepath(dirs);
        String sep = System.getProperty("path.separator");
        assertEquals("/path/a" + sep + "/path/b", sourcepath.toString());
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
