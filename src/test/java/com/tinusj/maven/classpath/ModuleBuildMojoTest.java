package com.tinusj.maven.classpath;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModuleBuildMojoTest {

    @TempDir
    File tempFolder;

    private ModuleBuildMojo mojo;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new ModuleBuildMojo();

        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setOutputDirectory(new File(tempFolder, "classes").getAbsolutePath());
        project.setBuild(build);

        setField(mojo, "project", project);
        setField(mojo, "defaultSource", "21");
        setField(mojo, "defaultTarget", "21");
        setField(mojo, "skip", false);
    }

    // =====================================================================
    // Module Resolution Tests
    // =====================================================================

    @Test
    public void testResolveModulesEmpty() throws Exception {
        setField(mojo, "buildModules", Collections.emptyList());
        List<BuildModule> resolved = mojo.resolveModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testResolveModulesNull() throws Exception {
        setField(mojo, "buildModules", null);
        List<BuildModule> resolved = mojo.resolveModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testResolveModulesSkipsNoSourceDirsNoOutputNoSteps() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("no-sources");
        // No source directories, no output directory, no WAR package — should be skipped

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testResolveModulesAcceptsModuleWithOutputDirOnly() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("output-only");
        mod.setOutputDirectory(new File(tempFolder, "out"));

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveModules();
        assertEquals(1, resolved.size());
    }

    @Test
    public void testResolveModulesAcceptsWarOnlyModule() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("war-only");
        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        war.setEnabled(true);
        war.setWarSourceDirectory(new File(tempFolder, "web"));
        war.setWarFile(new File(tempFolder, "out.war"));
        mod.setWarPackage(war);

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveModules();
        assertEquals(1, resolved.size());
    }

    @Test
    public void testResolveModulesAcceptsValidModules() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("valid");
        mod.setSourceDirectories(Arrays.asList("/some/source"));
        mod.setOutputDirectory(new File(tempFolder, "out"));

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveModules();
        assertEquals(1, resolved.size());
        assertEquals("valid", resolved.get(0).getName());
    }

    @Test
    public void testResolveModulesMultiple() throws Exception {
        BuildModule mod1 = new BuildModule();
        mod1.setName("mod1");
        mod1.setSourceDirectories(Arrays.asList("/src1"));

        BuildModule mod2 = new BuildModule();
        mod2.setName("mod2");
        mod2.setSourceDirectories(Arrays.asList("/src2"));

        BuildModule mod3 = new BuildModule();
        mod3.setName("no-src");

        setField(mojo, "buildModules", Arrays.asList(mod1, mod2, mod3));
        List<BuildModule> resolved = mojo.resolveModules();
        assertEquals(2, resolved.size());
    }

    // =====================================================================
    // Source Directory Collection Tests
    // =====================================================================

    @Test
    public void testCollectAllSourceDirectories() throws Exception {
        File srcA = new File(tempFolder, "mod-a-src");
        srcA.mkdirs();
        File srcB = new File(tempFolder, "mod-b-src");
        srcB.mkdirs();

        BuildModule modA = new BuildModule();
        modA.setSourceDirectories(Arrays.asList(srcA.getAbsolutePath()));
        BuildModule modB = new BuildModule();
        modB.setSourceDirectories(Arrays.asList(srcB.getAbsolutePath()));

        List<String> dirs = mojo.collectAllSourceDirectories(Arrays.asList(modA, modB));
        assertEquals(2, dirs.size());
        assertTrue(dirs.contains(srcA.getAbsolutePath()));
        assertTrue(dirs.contains(srcB.getAbsolutePath()));
    }

    @Test
    public void testCollectAllSourceDirectoriesNoDuplicates() throws Exception {
        File src = new File(tempFolder, "shared-src");
        src.mkdirs();

        BuildModule modA = new BuildModule();
        modA.setSourceDirectories(Arrays.asList(src.getAbsolutePath()));
        BuildModule modB = new BuildModule();
        modB.setSourceDirectories(Arrays.asList(src.getAbsolutePath()));

        List<String> dirs = mojo.collectAllSourceDirectories(Arrays.asList(modA, modB));
        assertEquals(1, dirs.size());
    }

    @Test
    public void testCollectAllSourceDirectoriesSkipsNonExistent() throws Exception {
        File src = new File(tempFolder, "real-src");
        src.mkdirs();

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(src.getAbsolutePath(), "/nonexistent/dir"));

        List<String> dirs = mojo.collectAllSourceDirectories(Arrays.asList(mod));
        assertEquals(1, dirs.size());
        assertEquals(src.getAbsolutePath(), dirs.get(0));
    }

    // =====================================================================
    // BuildModule Configuration Tests
    // =====================================================================

    @Test
    public void testBuildModuleDisplayNameFromName() {
        BuildModule mod = new BuildModule();
        mod.setName("webclient");
        assertEquals("webclient", mod.getDisplayName());
    }

    @Test
    public void testBuildModuleDisplayNameFallsBackToOutputDir() {
        BuildModule mod = new BuildModule();
        mod.setOutputDirectory(new File("/path/to/output"));
        assertEquals("output", mod.getDisplayName());
    }

    @Test
    public void testBuildModuleDisplayNameUnnamed() {
        BuildModule mod = new BuildModule();
        assertEquals("unnamed", mod.getDisplayName());
    }

    @Test
    public void testBuildModuleEcjEnabled() {
        BuildModule mod = new BuildModule();
        assertFalse(mod.isEcjEnabled());

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        mod.setEcjCompile(ecj);
        assertTrue(mod.isEcjEnabled());
    }

    @Test
    public void testBuildModuleGwtEnabled() {
        BuildModule mod = new BuildModule();
        assertFalse(mod.isGwtEnabled());

        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        mod.setGwtCompile(gwt);
        assertTrue(mod.isGwtEnabled());
    }

    @Test
    public void testBuildModuleCxfEnabled() {
        BuildModule mod = new BuildModule();
        assertFalse(mod.isCxfEnabled());

        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        mod.setCxfCompile(cxf);
        assertTrue(mod.isCxfEnabled());
    }

    // =====================================================================
    // ECJ Argument Building Tests
    // =====================================================================

    @Test
    public void testBuildEcjArgumentsBasic() throws Exception {
        File srcDir = new File(tempFolder, "ecj-src");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-out");
        outDir.mkdirs();

        createFile(new File(srcDir, "Test.java"), "public class Test {}");

        BuildModule mod = new BuildModule();
        mod.setName("ecj-test");
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        ecj.setSource("21");
        ecj.setTarget("21");

        List<String> args = mojo.buildEcjArguments(mod, ecj,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.of("/some/dep.jar"));

        assertTrue(args.contains("-source"));
        assertTrue(args.contains("21"));
        assertTrue(args.contains("-target"));
        assertTrue(args.contains("-d"));
        assertTrue(args.contains(outDir.getAbsolutePath()));
        assertTrue(args.contains("-classpath"));
        assertTrue(args.contains("-sourcepath"));
        assertTrue(args.contains("-nowarn"));
    }

    @Test
    public void testBuildEcjArgumentsWithPropertiesFile() throws Exception {
        File srcDir = new File(tempFolder, "ecj-src2");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-out2");
        outDir.mkdirs();
        File propsFile = new File(tempFolder, "ecj.prefs");
        propsFile.createNewFile();

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        ecj.setPropertiesFile(propsFile);

        List<String> args = mojo.buildEcjArguments(mod, ecj,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.empty());

        assertTrue(args.contains("-properties"));
        assertTrue(args.contains(propsFile.getAbsolutePath()));
    }

    @Test
    public void testBuildEcjArgumentsWithEncoding() throws Exception {
        File srcDir = new File(tempFolder, "ecj-src3");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-out3");

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        ecj.setEncoding("Cp1252");

        List<String> args = mojo.buildEcjArguments(mod, ecj,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.empty());

        assertTrue(args.contains("-encoding"));
        assertTrue(args.contains("Cp1252"));
    }

    @Test
    public void testBuildEcjArgumentsUsesDefaultSourceTarget() throws Exception {
        File srcDir = new File(tempFolder, "ecj-src4");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-out4");

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        // ECJ settings without explicit source/target - should use defaults
        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);

        List<String> args = mojo.buildEcjArguments(mod, ecj,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.empty());

        assertTrue(args.contains("-source"));
        assertTrue(args.contains("21")); // from defaultSource
        assertTrue(args.contains("-target"));
    }

    @Test
    public void testBuildEcjArgumentsWithCompilerArgs() throws Exception {
        File srcDir = new File(tempFolder, "ecj-src5");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-out5");

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        ecj.setCompilerArguments(Arrays.asList("-XDignore.symbol.file", "-time"));

        List<String> args = mojo.buildEcjArguments(mod, ecj,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.empty());

        assertTrue(args.contains("-XDignore.symbol.file"));
        assertTrue(args.contains("-time"));
    }

    @Test
    public void testBuildEcjArgumentsNoDebug() throws Exception {
        File srcDir = new File(tempFolder, "ecj-src6");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-out6");

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        ecj.setDebug(false);

        List<String> args = mojo.buildEcjArguments(mod, ecj,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.empty());

        assertTrue(args.contains("-g:none"));
    }

    // =====================================================================
    // GWT Argument Building Tests
    // =====================================================================

    @Test
    public void testBuildGwtArgumentsBasic() {
        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        gwt.setWarDirectory("/project/GWT/war");
        gwt.setGwtModules(Arrays.asList("com.example.MyModule"));

        List<String> args = mojo.buildGwtArguments(gwt);

        assertTrue(args.contains("-failOnError"));
        assertTrue(args.contains("-war"));
        assertTrue(args.contains("/project/GWT/war"));
        assertTrue(args.contains("-style"));
        assertTrue(args.contains("OBF"));
        assertTrue(args.contains("-logLevel"));
        assertTrue(args.contains("INFO"));
        assertTrue(args.contains("com.example.MyModule"));
    }

    @Test
    public void testBuildGwtArgumentsFullConfig() {
        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        gwt.setWarDirectory("/war");
        gwt.setStyle("PRETTY");
        gwt.setLogLevel("DEBUG");
        gwt.setLocalWorkers("4");
        gwt.setOptimize("9");
        gwt.setWorkDir("/tmp/work");
        gwt.setExtraDir("/tmp/extra");
        gwt.setSaveSource(true);
        gwt.setStrict(true);
        gwt.setFailOnError(true);
        gwt.setMethodNameDisplayMode("FULL");
        gwt.setGwtModules(Arrays.asList("com.example.Mod1", "com.example.Mod2"));

        List<String> args = mojo.buildGwtArguments(gwt);

        assertTrue(args.contains("-failOnError"));
        assertTrue(args.contains("-XmethodNameDisplayMode"));
        assertTrue(args.contains("FULL"));
        assertTrue(args.contains("-war"));
        assertTrue(args.contains("/war"));
        assertTrue(args.contains("-strict"));
        assertTrue(args.contains("-style"));
        assertTrue(args.contains("PRETTY"));
        assertTrue(args.contains("-logLevel"));
        assertTrue(args.contains("DEBUG"));
        assertTrue(args.contains("-localWorkers"));
        assertTrue(args.contains("4"));
        assertTrue(args.contains("-optimize"));
        assertTrue(args.contains("9"));
        assertTrue(args.contains("-workDir"));
        assertTrue(args.contains("/tmp/work"));
        assertTrue(args.contains("-extra"));
        assertTrue(args.contains("/tmp/extra"));
        assertTrue(args.contains("-saveSource"));
        assertTrue(args.contains("com.example.Mod1"));
        assertTrue(args.contains("com.example.Mod2"));
    }

    @Test
    public void testBuildGwtCommandContainsMainClass() {
        BuildModule mod = new BuildModule();
        mod.setName("gwt-test");
        mod.setSourceDirectories(Arrays.asList("/src"));

        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        gwt.setWarDirectory("/war");
        gwt.setGwtModules(Arrays.asList("com.example.Mod"));

        List<String> cmd = mojo.buildGwtCommand(mod, gwt, ClassPath.empty());

        assertTrue(cmd.get(0).contains("java"));
        assertTrue(cmd.contains("com.google.gwt.dev.Compiler"));
        assertTrue(cmd.contains("com.example.Mod"));
    }

    @Test
    public void testBuildGwtCommandWithJvmArgs() {
        BuildModule mod = new BuildModule();
        mod.setName("gwt-jvm");
        mod.setSourceDirectories(Arrays.asList("/src"));

        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        gwt.setWarDirectory("/war");
        gwt.setGwtModules(Arrays.asList("com.example.Mod"));
        gwt.setJvmArguments(Arrays.asList("-Xmx1g"));

        Map<String, String> sysProps = new LinkedHashMap<>();
        sysProps.put("java.io.tmpdir", "/tmp/gwt");
        gwt.setSystemProperties(sysProps);

        List<String> cmd = mojo.buildGwtCommand(mod, gwt, ClassPath.empty());

        assertTrue(cmd.contains("-Xmx1g"));
        assertTrue(cmd.contains("-Djava.io.tmpdir=/tmp/gwt"));
    }

    // =====================================================================
    // CXF Argument Building Tests
    // =====================================================================

    @Test
    public void testBuildCxfArgumentsBasic() {
        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        cxf.setServiceClass("com.example.OffsiteImpl");
        cxf.setOutputDirectory("/WEB-INF/wsdl");
        cxf.setOutputFile("Offsite.wsdl");

        List<String> args = mojo.buildCxfArguments(cxf);

        assertTrue(args.contains("-wsdl"));
        assertTrue(args.contains("-d"));
        assertTrue(args.contains("/WEB-INF/wsdl"));
        assertTrue(args.contains("-o"));
        assertTrue(args.contains("Offsite.wsdl"));
        assertTrue(args.contains("com.example.OffsiteImpl"));
    }

    @Test
    public void testBuildCxfArgumentsWithXsdImports() {
        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        cxf.setServiceClass("com.example.Service");
        cxf.setCreateXsdImports(true);

        List<String> args = mojo.buildCxfArguments(cxf);

        assertTrue(args.contains("-wsdl"));
        assertTrue(args.contains("-createxsdimports"));
        assertTrue(args.contains("com.example.Service"));
    }

    @Test
    public void testBuildCxfArgumentsWithAdditionalArgs() {
        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        cxf.setServiceClass("com.example.Service");
        cxf.setArguments(Arrays.asList("-verbose", "-frontend", "jaxws"));

        List<String> args = mojo.buildCxfArguments(cxf);

        assertTrue(args.contains("-verbose"));
        assertTrue(args.contains("-frontend"));
        assertTrue(args.contains("jaxws"));
    }

    @Test
    public void testBuildCxfCommandContainsMainClass() {
        BuildModule mod = new BuildModule();
        mod.setName("cxf-test");

        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        cxf.setServiceClass("com.example.OffsiteImpl");
        cxf.setOutputDirectory("/wsdl");
        cxf.setOutputFile("Offsite.wsdl");

        List<String> cmd = mojo.buildCxfCommand(mod, cxf, ClassPath.empty());

        assertTrue(cmd.get(0).contains("java"));
        assertTrue(cmd.contains("org.apache.cxf.tools.java2ws.JavaToWS"));
        assertTrue(cmd.contains("com.example.OffsiteImpl"));
    }

    @Test
    public void testBuildCxfCommandNoWsdlGeneration() {
        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        cxf.setGenerateWsdl(false);
        cxf.setServiceClass("com.example.Service");

        List<String> args = mojo.buildCxfArguments(cxf);

        assertFalse(args.contains("-wsdl"));
    }

    // =====================================================================
    // Module Classpath Tests
    // =====================================================================

    @Test
    public void testBuildModuleClasspath() throws Exception {
        File out1 = new File(tempFolder, "out1");
        File out2 = new File(tempFolder, "out2");

        BuildModule mod1 = new BuildModule();
        mod1.setOutputDirectory(out1);
        BuildModule mod2 = new BuildModule();
        mod2.setOutputDirectory(out2);
        mod2.setClasspathEntries(Arrays.asList("/extra/lib.jar"));

        ClassPath cp = mojo.buildModuleClasspath(
                Arrays.asList(mod1, mod2), ClassPath.of("/dep.jar"), mod2);

        String result = cp.toString();
        assertTrue(result.contains(out1.getAbsolutePath()));
        assertTrue(result.contains(out2.getAbsolutePath()));
        assertTrue(result.contains("/dep.jar"));
        assertTrue(result.contains("/extra/lib.jar"));
    }

    // =====================================================================
    // Skip / No-steps Tests
    // =====================================================================

    @Test
    public void testExecuteSkip() throws Exception {
        setField(mojo, "skip", true);
        setField(mojo, "buildModules", null);
        // Should not throw
        mojo.execute();
    }

    @Test
    public void testExecuteNoModules() throws Exception {
        setField(mojo, "buildModules", Collections.emptyList());
        // Should not throw
        mojo.execute();
    }

    // =====================================================================
    // Integration-style Tests: Mixed Module Configurations
    // =====================================================================

    @Test
    public void testMixedModuleConfigurationEcjPlusGwt() {
        BuildModule mod = new BuildModule();
        mod.setName("webclient");

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        ecj.setSource("21");
        ecj.setTarget("21");
        mod.setEcjCompile(ecj);

        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        gwt.setWarDirectory("/GWT/war");
        gwt.setGwtModules(Arrays.asList("com.example.WebClient"));
        mod.setGwtCompile(gwt);

        assertTrue(mod.isEcjEnabled());
        assertTrue(mod.isGwtEnabled());
        assertFalse(mod.isCxfEnabled());
    }

    @Test
    public void testMixedModuleConfigurationGwtOnly() {
        BuildModule mod = new BuildModule();
        mod.setName("demo");

        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setEnabled(true);
        gwt.setWarDirectory("/GWT/war");
        gwt.setGwtModules(Arrays.asList("com.example.Demo"));
        mod.setGwtCompile(gwt);

        assertFalse(mod.isEcjEnabled());
        assertTrue(mod.isGwtEnabled());
        assertFalse(mod.isCxfEnabled());
    }

    @Test
    public void testMixedModuleConfigurationEcjPlusCxf() {
        BuildModule mod = new BuildModule();
        mod.setName("webservice");

        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        ecj.setEnabled(true);
        mod.setEcjCompile(ecj);

        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        cxf.setEnabled(true);
        cxf.setServiceClass("com.example.OffsiteImpl");
        cxf.setOutputDirectory("/WEB-INF/wsdl");
        cxf.setOutputFile("Offsite.wsdl");
        cxf.setCreateXsdImports(true);
        mod.setCxfCompile(cxf);

        assertTrue(mod.isEcjEnabled());
        assertFalse(mod.isGwtEnabled());
        assertTrue(mod.isCxfEnabled());
    }

    @Test
    public void testCollectJavaFiles() throws Exception {
        File srcDir = new File(tempFolder, "java-files");
        srcDir.mkdirs();
        createFile(new File(srcDir, "A.java"), "public class A {}");
        createFile(new File(srcDir, "readme.txt"), "not java");
        File sub = new File(srcDir, "sub");
        sub.mkdirs();
        createFile(new File(sub, "B.java"), "public class B {}");

        List<File> files = mojo.collectJavaFiles(Arrays.asList(srcDir.getAbsolutePath()));
        assertEquals(2, files.size());
    }

    @Test
    public void testCollectJavaFilesNullDirectories() throws Exception {
        List<File> files = mojo.collectJavaFiles(null);
        assertTrue(files.isEmpty());
    }

    // =====================================================================
    // EcjCompileSettings Defaults Tests
    // =====================================================================

    @Test
    public void testEcjCompileSettingsDefaults() {
        BuildModule.EcjCompileSettings ecj = new BuildModule.EcjCompileSettings();
        assertFalse(ecj.isEnabled());
        assertTrue(ecj.isNowarn());
        assertTrue(ecj.isFailOnError());
        assertTrue(ecj.isDebug());
    }

    // =====================================================================
    // GwtCompileSettings Defaults Tests
    // =====================================================================

    @Test
    public void testGwtCompileSettingsDefaults() {
        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        assertFalse(gwt.isEnabled());
        assertEquals("OBF", gwt.getStyle());
        assertEquals("INFO", gwt.getLogLevel());
        assertTrue(gwt.isFailOnError());
        assertFalse(gwt.isStrict());
        assertFalse(gwt.isSaveSource());
    }

    // =====================================================================
    // CxfCompileSettings Defaults Tests
    // =====================================================================

    @Test
    public void testCxfCompileSettingsDefaults() {
        BuildModule.CxfCompileSettings cxf = new BuildModule.CxfCompileSettings();
        assertFalse(cxf.isEnabled());
        assertTrue(cxf.isGenerateWsdl());
        assertFalse(cxf.isCreateXsdImports());
    }

    // =====================================================================
    // Global Defaults Merge Tests
    // =====================================================================

    @Test
    public void testEcjMergeGlobalDefaultsApplied() throws Exception {
        BuildModule.EcjCompileSettings global = new BuildModule.EcjCompileSettings();
        global.setSource("21");
        global.setTarget("21");
        global.setEncoding("Cp1252");
        global.setDebug(false);

        // Module only sets enabled, everything else comes from global
        BuildModule.EcjCompileSettings moduleEcj = new BuildModule.EcjCompileSettings();
        moduleEcj.setEnabled(true);
        moduleEcj.setDebug(false); // Explicitly match global

        BuildModule.EcjCompileSettings merged = BuildModule.EcjCompileSettings.merge(global, moduleEcj);

        assertTrue(merged.isEnabled());
        assertEquals("21", merged.getSource());
        assertEquals("21", merged.getTarget());
        assertEquals("Cp1252", merged.getEncoding());
        assertFalse(merged.isDebug());
    }

    @Test
    public void testEcjMergeModuleOverridesGlobal() throws Exception {
        BuildModule.EcjCompileSettings global = new BuildModule.EcjCompileSettings();
        global.setSource("17");
        global.setTarget("17");
        global.setEncoding("UTF-8");

        BuildModule.EcjCompileSettings moduleEcj = new BuildModule.EcjCompileSettings();
        moduleEcj.setEnabled(true);
        moduleEcj.setSource("21");
        // target not set, encoding not set — should come from global

        BuildModule.EcjCompileSettings merged = BuildModule.EcjCompileSettings.merge(global, moduleEcj);

        assertEquals("21", merged.getSource()); // overridden
        assertEquals("17", merged.getTarget()); // from global
        assertEquals("UTF-8", merged.getEncoding()); // from global
    }

    @Test
    public void testEcjMergeNullGlobal() {
        BuildModule.EcjCompileSettings moduleEcj = new BuildModule.EcjCompileSettings();
        moduleEcj.setEnabled(true);
        moduleEcj.setSource("21");

        BuildModule.EcjCompileSettings merged = BuildModule.EcjCompileSettings.merge(null, moduleEcj);
        assertEquals("21", merged.getSource());
        assertTrue(merged.isEnabled());
    }

    @Test
    public void testEcjMergeNullModule() {
        BuildModule.EcjCompileSettings global = new BuildModule.EcjCompileSettings();
        global.setSource("21");

        BuildModule.EcjCompileSettings merged = BuildModule.EcjCompileSettings.merge(global, null);
        assertEquals("21", merged.getSource());
    }

    @Test
    public void testEcjMergeBothNull() {
        assertNull(BuildModule.EcjCompileSettings.merge(null, null));
    }

    @Test
    public void testGwtMergeGlobalDefaultsApplied() {
        BuildModule.GwtCompileSettings global = new BuildModule.GwtCompileSettings();
        global.setWarDirectory("/global/war");
        global.setStyle("OBF");
        global.setLocalWorkers("4");

        BuildModule.GwtCompileSettings moduleGwt = new BuildModule.GwtCompileSettings();
        moduleGwt.setEnabled(true);
        moduleGwt.setGwtModules(Arrays.asList("com.example.MyModule"));

        BuildModule.GwtCompileSettings merged = BuildModule.GwtCompileSettings.merge(global, moduleGwt);

        assertTrue(merged.isEnabled());
        assertEquals("/global/war", merged.getWarDirectory());
        assertEquals("OBF", merged.getStyle());
        assertEquals("4", merged.getLocalWorkers());
        assertEquals(Arrays.asList("com.example.MyModule"), merged.getGwtModules());
    }

    @Test
    public void testGwtMergeModuleOverridesGlobal() {
        BuildModule.GwtCompileSettings global = new BuildModule.GwtCompileSettings();
        global.setWarDirectory("/global/war");
        global.setStyle("OBF");

        BuildModule.GwtCompileSettings moduleGwt = new BuildModule.GwtCompileSettings();
        moduleGwt.setEnabled(true);
        moduleGwt.setStyle("PRETTY");

        BuildModule.GwtCompileSettings merged = BuildModule.GwtCompileSettings.merge(global, moduleGwt);

        assertEquals("PRETTY", merged.getStyle());
        assertEquals("/global/war", merged.getWarDirectory());
    }

    @Test
    public void testCxfMergeGlobalDefaultsApplied() {
        BuildModule.CxfCompileSettings global = new BuildModule.CxfCompileSettings();
        global.setCreateXsdImports(true);

        BuildModule.CxfCompileSettings moduleCxf = new BuildModule.CxfCompileSettings();
        moduleCxf.setEnabled(true);
        moduleCxf.setServiceClass("com.example.Service");
        moduleCxf.setCreateXsdImports(true); // Explicitly match global

        BuildModule.CxfCompileSettings merged = BuildModule.CxfCompileSettings.merge(global, moduleCxf);

        assertTrue(merged.isEnabled());
        assertTrue(merged.isCreateXsdImports());
        assertEquals("com.example.Service", merged.getServiceClass());
    }

    @Test
    public void testGlobalDefaultsUsedInBuildEcjArguments() throws Exception {
        File srcDir = new File(tempFolder, "ecj-global-src");
        srcDir.mkdirs();
        File outDir = new File(tempFolder, "ecj-global-out");

        // Set global defaults on the mojo
        BuildModule.EcjCompileSettings global = new BuildModule.EcjCompileSettings();
        global.setSource("21");
        global.setTarget("21");
        global.setEncoding("Cp1252");
        setField(mojo, "defaultEcjCompile", global);

        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList(srcDir.getAbsolutePath()));
        mod.setOutputDirectory(outDir);

        // Module only has enabled=true, no source/target/encoding
        BuildModule.EcjCompileSettings moduleEcj = new BuildModule.EcjCompileSettings();
        moduleEcj.setEnabled(true);

        BuildModule.EcjCompileSettings merged = BuildModule.EcjCompileSettings.merge(global, moduleEcj);
        List<String> args = mojo.buildEcjArguments(mod, merged,
                Arrays.asList(srcDir.getAbsolutePath()), ClassPath.empty());

        assertTrue(args.contains("-source"));
        assertTrue(args.contains("21"));
        assertTrue(args.contains("-encoding"));
        assertTrue(args.contains("Cp1252"));
    }

    // =====================================================================
    // WAR Packaging Tests
    // =====================================================================

    @Test
    public void testWarPackageSettingsDefaults() {
        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        assertFalse(war.isEnabled());
        assertTrue(war.isIncludeClasses());
    }

    @Test
    public void testWarMergeGlobalDefaultsApplied() {
        BuildModule.WarPackageSettings global = new BuildModule.WarPackageSettings();
        global.setWarSourceDirectory(new File("/global/war"));

        BuildModule.WarPackageSettings moduleWar = new BuildModule.WarPackageSettings();
        moduleWar.setEnabled(true);
        moduleWar.setWarFile(new File("/out/myapp.war"));

        BuildModule.WarPackageSettings merged = BuildModule.WarPackageSettings.merge(global, moduleWar);

        assertTrue(merged.isEnabled());
        assertEquals(new File("/global/war"), merged.getWarSourceDirectory());
        assertEquals(new File("/out/myapp.war"), merged.getWarFile());
    }

    @Test
    public void testWarMergeBothNull() {
        assertNull(BuildModule.WarPackageSettings.merge(null, null));
    }

    @Test
    public void testBuildModuleWarEnabled() {
        BuildModule mod = new BuildModule();
        assertFalse(mod.isWarEnabled());

        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        war.setEnabled(true);
        mod.setWarPackage(war);
        assertTrue(mod.isWarEnabled());
    }

    @Test
    public void testExecuteWarPackageCreatesWarFile() throws Exception {
        // Create WAR source directory with web.xml
        File warSrcDir = new File(tempFolder, "war-src");
        File webInfDir = new File(warSrcDir, "WEB-INF");
        webInfDir.mkdirs();
        createFile(new File(webInfDir, "web.xml"),
                "<?xml version=\"1.0\"?>\n<web-app></web-app>");

        File warFile = new File(tempFolder, "output.war");

        BuildModule mod = new BuildModule();
        mod.setName("war-test");

        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        war.setEnabled(true);
        war.setWarSourceDirectory(warSrcDir);
        war.setWarFile(warFile);
        war.setIncludeClasses(false);

        mojo.executeWarPackage(mod, war);

        assertTrue(warFile.exists(), "WAR file should exist");
        assertTrue(warFile.length() > 0, "WAR file should not be empty");

        // Verify contents
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(warFile)) {
            assertNotNull(jar.getEntry("WEB-INF/"), "WEB-INF/ should exist in WAR");
            assertNotNull(jar.getEntry("WEB-INF/web.xml"), "WEB-INF/web.xml should exist in WAR");
        }
    }

    @Test
    public void testExecuteWarPackageWithClasses() throws Exception {
        // Create WAR source directory with web.xml
        File warSrcDir = new File(tempFolder, "war-src2");
        File webInfDir = new File(warSrcDir, "WEB-INF");
        webInfDir.mkdirs();
        createFile(new File(webInfDir, "web.xml"), "<web-app/>");

        // Create classes directory
        File classesDir = new File(tempFolder, "classes2");
        classesDir.mkdirs();
        createFile(new File(classesDir, "Hello.class"), "fake class content");

        File warFile = new File(tempFolder, "output-with-classes.war");

        BuildModule mod = new BuildModule();
        mod.setName("war-classes");
        mod.setOutputDirectory(classesDir);

        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        war.setEnabled(true);
        war.setWarSourceDirectory(warSrcDir);
        war.setWarFile(warFile);
        war.setIncludeClasses(true);

        mojo.executeWarPackage(mod, war);

        assertTrue(warFile.exists());
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(warFile)) {
            assertNotNull(jar.getEntry("WEB-INF/web.xml"));
            assertNotNull(jar.getEntry("WEB-INF/classes/Hello.class"),
                    "Compiled classes should be in WEB-INF/classes/");
        }
    }

    @Test
    public void testExecuteWarPackageSkipsNonExistentSourceDir() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("war-missing-src");

        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        war.setEnabled(true);
        war.setWarSourceDirectory(new File("/nonexistent/war-dir"));
        war.setWarFile(new File(tempFolder, "missing.war"));

        // Should not throw, just log warning
        mojo.executeWarPackage(mod, war);

        assertFalse(new File(tempFolder, "missing.war").exists());
    }

    @Test
    public void testExecuteWarPackageNullWarFile() throws Exception {
        File warSrcDir = new File(tempFolder, "war-null-file");
        warSrcDir.mkdirs();

        BuildModule mod = new BuildModule();
        mod.setName("war-null-file");

        BuildModule.WarPackageSettings war = new BuildModule.WarPackageSettings();
        war.setEnabled(true);
        war.setWarSourceDirectory(warSrcDir);
        war.setWarFile(null);

        assertThrows(MojoExecutionException.class, () -> mojo.executeWarPackage(mod, war));
    }

    // =====================================================================
    // Utility
    // =====================================================================

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
