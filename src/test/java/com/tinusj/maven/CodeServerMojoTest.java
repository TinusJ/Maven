package com.tinusj.maven;

import com.tinusj.maven.config.BuildModule;
import com.tinusj.maven.support.ClassPath;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodeServerMojoTest {

    @TempDir
    File tempFolder;

    private CodeServerMojo mojo;

    @BeforeEach
    public void setUp() throws Exception {
        mojo = new CodeServerMojo();

        MavenProject project = new MavenProject();
        Build build = new Build();
        build.setOutputDirectory(new File(tempFolder, "classes").getAbsolutePath());
        project.setBuild(build);

        setField(mojo, "project", project);
        setField(mojo, "skip", false);
    }

    // =====================================================================
    // Module Resolution Tests
    // =====================================================================

    @Test
    public void testResolveCodeServerModulesEmpty() throws Exception {
        setField(mojo, "buildModules", Collections.emptyList());
        List<BuildModule> resolved = mojo.resolveCodeServerModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testResolveCodeServerModulesNull() throws Exception {
        setField(mojo, "buildModules", null);
        List<BuildModule> resolved = mojo.resolveCodeServerModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testResolveCodeServerModulesSkipsDisabled() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("no-codeserver");
        // No codeServer settings — should be skipped

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveCodeServerModules();
        assertTrue(resolved.isEmpty());
    }

    @Test
    public void testResolveCodeServerModulesIncludesEnabled() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("webclient");
        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        cs.setEnabled(true);
        mod.setCodeServer(cs);

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveCodeServerModules();
        assertEquals(1, resolved.size());
        assertEquals("webclient", resolved.get(0).getName());
    }

    @Test
    public void testResolveCodeServerModulesViaGlobalDefaults() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("webclient");
        // No module-level codeServer, but global default is enabled

        BuildModule.CodeServerSettings globalCs = new BuildModule.CodeServerSettings();
        globalCs.setEnabled(true);
        setField(mojo, "defaultCodeServer", globalCs);

        setField(mojo, "buildModules", Arrays.asList(mod));
        List<BuildModule> resolved = mojo.resolveCodeServerModules();
        assertEquals(1, resolved.size());
    }

    @Test
    public void testResolveCodeServerModulesMultiple() throws Exception {
        BuildModule mod1 = new BuildModule();
        mod1.setName("mod1");
        BuildModule.CodeServerSettings cs1 = new BuildModule.CodeServerSettings();
        cs1.setEnabled(true);
        mod1.setCodeServer(cs1);

        BuildModule mod2 = new BuildModule();
        mod2.setName("mod2");
        // mod2 has no codeserver

        BuildModule mod3 = new BuildModule();
        mod3.setName("mod3");
        BuildModule.CodeServerSettings cs3 = new BuildModule.CodeServerSettings();
        cs3.setEnabled(true);
        mod3.setCodeServer(cs3);

        setField(mojo, "buildModules", Arrays.asList(mod1, mod2, mod3));
        List<BuildModule> resolved = mojo.resolveCodeServerModules();
        assertEquals(2, resolved.size());
        assertEquals("mod1", resolved.get(0).getName());
        assertEquals("mod3", resolved.get(1).getName());
    }

    // =====================================================================
    // GWT Module Resolution Tests
    // =====================================================================

    @Test
    public void testResolveGwtModulesFromCodeServerSettings() {
        BuildModule mod = new BuildModule();
        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        cs.setGwtModules(Arrays.asList("com.example.ModuleA"));

        List<String> modules = mojo.resolveGwtModules(mod, cs);
        assertNotNull(modules);
        assertEquals(1, modules.size());
        assertEquals("com.example.ModuleA", modules.get(0));
    }

    @Test
    public void testResolveGwtModulesFallsBackToGwtCompile() {
        BuildModule mod = new BuildModule();
        BuildModule.GwtCompileSettings gwt = new BuildModule.GwtCompileSettings();
        gwt.setGwtModules(Arrays.asList("com.example.FallbackModule"));
        mod.setGwtCompile(gwt);

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        // No gwtModules in CodeServer settings

        List<String> modules = mojo.resolveGwtModules(mod, cs);
        assertNotNull(modules);
        assertEquals(1, modules.size());
        assertEquals("com.example.FallbackModule", modules.get(0));
    }

    @Test
    public void testResolveGwtModulesReturnsNullWhenNoneConfigured() {
        BuildModule mod = new BuildModule();
        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();

        List<String> modules = mojo.resolveGwtModules(mod, cs);
        assertNull(modules);
    }

    // =====================================================================
    // CodeServer Command Building Tests
    // =====================================================================

    @Test
    public void testBuildCodeServerCommandMinimal() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("webclient");
        mod.setSourceDirectories(Arrays.asList("/src/main/java"));

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        cs.setEnabled(true);

        ClassPath classpath = ClassPath.of("/lib/gwt-dev.jar");
        List<String> gwtModules = Arrays.asList("com.example.WebClient");

        List<String> command = mojo.buildCodeServerCommand(mod, cs, classpath, gwtModules);

        assertTrue(command.get(0).contains("java"));
        assertTrue(command.contains(CodeServerMojo.CODESERVER_MAIN_CLASS));
        assertTrue(command.contains("com.example.WebClient"));
    }

    @Test
    public void testBuildCodeServerArgumentsWithAllOptions() {
        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList("/src/main/java", "/src/shared/java"));

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        cs.setWorkDir("/tmp/gwt/codeserver");
        cs.setLauncherDir("/webapp/war");
        cs.setBindAddress("0.0.0.0");
        cs.setPort("9876");
        cs.setLogLevel("INFO");
        cs.setCodeserverArgs(Arrays.asList("-noprecompile"));

        List<String> gwtModules = Arrays.asList("com.example.MyModule");

        List<String> args = mojo.buildCodeServerArguments(mod, cs, gwtModules);

        assertTrue(args.contains("-workDir"));
        assertTrue(args.contains("/tmp/gwt/codeserver"));
        assertTrue(args.contains("-launcherDir"));
        assertTrue(args.contains("/webapp/war"));
        assertTrue(args.contains("-bindAddress"));
        assertTrue(args.contains("0.0.0.0"));
        assertTrue(args.contains("-port"));
        assertTrue(args.contains("9876"));
        assertTrue(args.contains("-logLevel"));
        assertTrue(args.contains("INFO"));
        assertTrue(args.contains("-noprecompile"));
        assertTrue(args.contains("-allowMissingSrc"));
        assertTrue(args.contains("-src"));
        assertTrue(args.contains("/src/main/java"));
        assertTrue(args.contains("/src/shared/java"));
        assertTrue(args.contains("com.example.MyModule"));
    }

    @Test
    public void testBuildCodeServerArgumentsMinimal() {
        BuildModule mod = new BuildModule();
        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        List<String> gwtModules = Arrays.asList("com.example.Module");

        List<String> args = mojo.buildCodeServerArguments(mod, cs, gwtModules);

        assertTrue(args.contains("-allowMissingSrc"));
        assertTrue(args.contains("com.example.Module"));
        // Should not contain optional flags when not configured
        assertFalse(args.contains("-workDir"));
        assertFalse(args.contains("-launcherDir"));
        assertFalse(args.contains("-bindAddress"));
        assertFalse(args.contains("-port"));
    }

    @Test
    public void testBuildCodeServerArgumentsSourceDirsAsFlags() {
        BuildModule mod = new BuildModule();
        mod.setSourceDirectories(Arrays.asList("/src1", "/src2"));

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        List<String> gwtModules = Arrays.asList("com.example.Module");

        List<String> args = mojo.buildCodeServerArguments(mod, cs, gwtModules);

        // Each source dir should be preceded by -src
        int src1Idx = args.indexOf("/src1");
        int src2Idx = args.indexOf("/src2");
        assertTrue(src1Idx > 0);
        assertTrue(src2Idx > 0);
        assertEquals("-src", args.get(src1Idx - 1));
        assertEquals("-src", args.get(src2Idx - 1));
    }

    @Test
    public void testBuildCodeServerCommandWithJvmArgs() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("test");
        mod.setSourceDirectories(Arrays.asList("/src"));

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        cs.setJvmArguments(Arrays.asList("-Xmx1g", "-Xms512m"));

        ClassPath classpath = ClassPath.of("/lib/gwt-dev.jar");
        List<String> gwtModules = Arrays.asList("com.example.Module");

        List<String> command = mojo.buildCodeServerCommand(mod, cs, classpath, gwtModules);

        assertTrue(command.contains("-Xmx1g"));
        assertTrue(command.contains("-Xms512m"));
        // JVM args should come before main class
        int xmxIdx = command.indexOf("-Xmx1g");
        int mainIdx = command.indexOf(CodeServerMojo.CODESERVER_MAIN_CLASS);
        assertTrue(xmxIdx < mainIdx);
    }

    @Test
    public void testBuildCodeServerCommandWithSystemProperties() throws Exception {
        BuildModule mod = new BuildModule();
        mod.setName("test");

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        Map<String, String> props = new LinkedHashMap<>();
        props.put("java.io.tmpdir", "/tmp/gwt");
        cs.setSystemProperties(props);

        ClassPath classpath = ClassPath.of("/lib/gwt-dev.jar");
        List<String> gwtModules = Arrays.asList("com.example.Module");

        List<String> command = mojo.buildCodeServerCommand(mod, cs, classpath, gwtModules);

        assertTrue(command.contains("-Djava.io.tmpdir=/tmp/gwt"));
    }

    // =====================================================================
    // BuildModule CodeServer Enabled Tests
    // =====================================================================

    @Test
    public void testBuildModuleCodeServerEnabled() {
        BuildModule mod = new BuildModule();
        assertFalse(mod.isCodeServerEnabled());

        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        cs.setEnabled(true);
        mod.setCodeServer(cs);
        assertTrue(mod.isCodeServerEnabled());
    }

    @Test
    public void testBuildModuleCodeServerDisabledByDefault() {
        BuildModule mod = new BuildModule();
        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        mod.setCodeServer(cs);
        assertFalse(mod.isCodeServerEnabled());
    }

    // =====================================================================
    // CodeServerSettings Merge Tests
    // =====================================================================

    @Test
    public void testCodeServerSettingsMergeBothNull() {
        assertNull(BuildModule.CodeServerSettings.merge(null, null));
    }

    @Test
    public void testCodeServerSettingsMergeDefaultsOnly() {
        BuildModule.CodeServerSettings defaults = new BuildModule.CodeServerSettings();
        defaults.setBindAddress("127.0.0.1");
        defaults.setPort("9876");

        BuildModule.CodeServerSettings result = BuildModule.CodeServerSettings.merge(defaults, null);
        assertNotNull(result);
        assertEquals("127.0.0.1", result.getBindAddress());
        assertEquals("9876", result.getPort());
    }

    @Test
    public void testCodeServerSettingsMergeOverrideOnly() {
        BuildModule.CodeServerSettings override = new BuildModule.CodeServerSettings();
        override.setEnabled(true);
        override.setPort("9877");

        BuildModule.CodeServerSettings result = BuildModule.CodeServerSettings.merge(null, override);
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertEquals("9877", result.getPort());
    }

    @Test
    public void testCodeServerSettingsMergeOverrideTakesPrecedence() {
        BuildModule.CodeServerSettings defaults = new BuildModule.CodeServerSettings();
        defaults.setBindAddress("127.0.0.1");
        defaults.setPort("9876");
        defaults.setLogLevel("INFO");

        BuildModule.CodeServerSettings override = new BuildModule.CodeServerSettings();
        override.setEnabled(true);
        override.setPort("9877");
        // bindAddress not set in override — should fall back to default

        BuildModule.CodeServerSettings result = BuildModule.CodeServerSettings.merge(defaults, override);
        assertNotNull(result);
        assertTrue(result.isEnabled());
        assertEquals("127.0.0.1", result.getBindAddress()); // from defaults
        assertEquals("9877", result.getPort()); // from override
        assertEquals("INFO", result.getLogLevel()); // from defaults
    }

    @Test
    public void testCodeServerSettingsMergeGwtModulesOverride() {
        BuildModule.CodeServerSettings defaults = new BuildModule.CodeServerSettings();
        defaults.setGwtModules(Arrays.asList("com.example.DefaultModule"));

        BuildModule.CodeServerSettings override = new BuildModule.CodeServerSettings();
        override.setEnabled(true);
        override.setGwtModules(Arrays.asList("com.example.OverrideModule"));

        BuildModule.CodeServerSettings result = BuildModule.CodeServerSettings.merge(defaults, override);
        assertEquals(1, result.getGwtModules().size());
        assertEquals("com.example.OverrideModule", result.getGwtModules().get(0));
    }

    @Test
    public void testCodeServerSettingsMergeGwtModulesFallback() {
        BuildModule.CodeServerSettings defaults = new BuildModule.CodeServerSettings();
        defaults.setGwtModules(Arrays.asList("com.example.DefaultModule"));

        BuildModule.CodeServerSettings override = new BuildModule.CodeServerSettings();
        override.setEnabled(true);
        // No gwtModules set in override

        BuildModule.CodeServerSettings result = BuildModule.CodeServerSettings.merge(defaults, override);
        assertEquals(1, result.getGwtModules().size());
        assertEquals("com.example.DefaultModule", result.getGwtModules().get(0));
    }

    @Test
    public void testCodeServerSettingsFailOnErrorDefault() {
        BuildModule.CodeServerSettings cs = new BuildModule.CodeServerSettings();
        assertTrue(cs.isFailOnError());
    }

    // =====================================================================
    // Module Classpath Building Tests
    // =====================================================================

    @Test
    public void testBuildModuleClasspath() {
        BuildModule mod1 = new BuildModule();
        mod1.setOutputDirectory(new File(tempFolder, "mod1-out"));
        mod1.getOutputDirectory().mkdirs();

        BuildModule mod2 = new BuildModule();
        mod2.setOutputDirectory(new File(tempFolder, "mod2-out"));
        mod2.getOutputDirectory().mkdirs();

        mod2.setClasspathEntries(Arrays.asList("/extra/lib.jar"));

        ClassPath depClasspath = ClassPath.empty();
        ClassPath result = mojo.buildModuleClasspath(Arrays.asList(mod1, mod2), depClasspath, mod2);

        String cpString = result.toString();
        assertTrue(cpString.contains("mod1-out"));
        assertTrue(cpString.contains("mod2-out"));
        assertTrue(cpString.contains("/extra/lib.jar"));
    }

    // =====================================================================
    // Utility
    // =====================================================================

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
