# module-build

Processes multiple build modules, each with its own configurable build steps: ECJ compilation, GWT compilation, CXF WSDL generation, and WAR packaging. This is the unified entry point for complex multi-module builds where different modules use different tool chains.

**Goal prefix:** `classpath`
**Default phase:** `compile`
**Requires dependency resolution:** `compile`

## Usage

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.tinusj.maven</groupId>
            <artifactId>classpath-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>module-build</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Plugin Parameters

| Parameter | Type | Property | Default | Description |
|-----------|------|----------|---------|-------------|
| `buildModules` | `List<BuildModule>` | — | — | **(Required)** The list of build modules to process. |
| `defaultSource` | `String` | `compiler.source` | `${maven.compiler.source}` | Default Java source version, used as a fallback for modules. |
| `defaultTarget` | `String` | `compiler.target` | `${maven.compiler.target}` | Default Java target version, used as a fallback for modules. |
| `skip` | `boolean` | `module-build.skip` | `false` | Skip execution of all module builds. |
| `defaultEcjCompile` | `EcjCompileSettings` | — | — | Global default ECJ settings applied to all modules (see below). |
| `defaultGwtCompile` | `GwtCompileSettings` | — | — | Global default GWT settings applied to all modules (see below). |
| `defaultCxfCompile` | `CxfCompileSettings` | — | — | Global default CXF settings applied to all modules (see below). |
| `defaultWarPackage` | `WarPackageSettings` | — | — | Global default WAR packaging settings applied to all modules (see below). |

## Build Module Parameters

Each module in `buildModules` supports:

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | `String` | Descriptive name for the module (used in log output). |
| `sourceDirectories` | `List<String>` | Source directories for this module. |
| `resourceDirectories` | `List<String>` | Resource directories for this module. |
| `outputDirectory` | `File` | Output directory for compiled classes. |
| `classpathEntries` | `List<String>` | Additional classpath entries for this module. |
| `ecjCompile` | `EcjCompileSettings` | ECJ compilation settings (see below). |
| `gwtCompile` | `GwtCompileSettings` | GWT compilation settings (see below). |
| `cxfCompile` | `CxfCompileSettings` | CXF WSDL generation settings (see below). |
| `warPackage` | `WarPackageSettings` | WAR packaging settings (see below). |

## Build Steps

Build steps are executed in the following order for each module:

1. **ECJ Compile** — Compiles Java sources using the Eclipse Compiler for Java
2. **GWT Compile** — Runs the GWT compiler as a forked Java process
3. **CXF Compile** — Runs the CXF JavaToWS tool for WSDL generation
4. **WAR Package** — Creates a WAR file from the module output

Each step is only executed when its `enabled` flag is `true`. Module-specific settings override global defaults.

---

### ECJ Compile Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Whether ECJ compilation is enabled. |
| `source` | `String` | — | Java source version (e.g. `21`). |
| `target` | `String` | — | Java target version (e.g. `21`). |
| `propertiesFile` | `File` | — | Path to an ECJ properties file. |
| `encoding` | `String` | — | Source encoding (e.g. `UTF-8`, `Cp1252`). |
| `nowarn` | `Boolean` | `true` | Whether to suppress warnings. |
| `failOnError` | `Boolean` | `true` | Whether to fail the build on compilation errors. |
| `debug` | `Boolean` | `true` | Whether to include debug information. |
| `compilerArguments` | `List<String>` | — | Additional compiler arguments. |
| `classpathEntries` | `List<String>` | — | Additional classpath entries for ECJ. |

### GWT Compile Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Whether GWT compilation is enabled. |
| `gwtModules` | `List<String>` | — | GWT modules to compile (e.g. `com.example.MyModule`). |
| `warDirectory` | `String` | — | Output WAR directory. |
| `style` | `String` | `OBF` | Compile style: `OBF`, `PRETTY`, or `DETAILED`. |
| `logLevel` | `String` | `INFO` | Log level: `ERROR`, `WARN`, `INFO`, `TRACE`, `DEBUG`, `SPAM`, `ALL`. |
| `localWorkers` | `String` | — | Number of local workers for parallel compilation. |
| `optimize` | `String` | — | Optimization level (0–9). |
| `workDir` | `String` | — | Working directory for GWT compilation. |
| `extraDir` | `String` | — | Extra output directory. |
| `saveSource` | `Boolean` | `false` | Whether to save generated source. |
| `strict` | `Boolean` | `false` | Whether to use strict mode. |
| `failOnError` | `Boolean` | `true` | Whether to fail the build on compilation errors. |
| `methodNameDisplayMode` | `String` | — | Method name display mode: `FULL`, `SHORT`, or `NONE`. |
| `classpathEntries` | `List<String>` | — | Additional classpath entries for GWT. |
| `jvmArguments` | `List<String>` | — | JVM arguments for the forked GWT process. |
| `systemProperties` | `Map<String, String>` | — | System properties for the forked GWT process. |

### CXF Compile Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Whether CXF WSDL generation is enabled. |
| `serviceClass` | `String` | — | Fully qualified service implementation class. |
| `outputDirectory` | `String` | — | Output directory for generated WSDL files. |
| `outputFile` | `String` | — | Output WSDL file name. |
| `generateWsdl` | `Boolean` | `true` | Whether to generate WSDL output. |
| `createXsdImports` | `Boolean` | `false` | Whether to create XSD imports. |
| `classpathEntries` | `List<String>` | — | Additional classpath entries for CXF. |
| `arguments` | `List<String>` | — | Additional arguments for the CXF tool. |

### WAR Package Settings

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Whether WAR packaging is enabled. |
| `warSourceDirectory` | `File` | — | Source directory with web content (e.g. WEB-INF/web.xml). |
| `warFile` | `File` | — | Output WAR file path. |
| `includeClasses` | `Boolean` | `true` | Include module's compiled classes in WEB-INF/classes. |
| `additionalContentDirectories` | `List<String>` | — | Additional directories to add to the WAR root. |
| `libEntries` | `List<String>` | — | Additional JAR files to include in WEB-INF/lib. |

## Examples

### ECJ + GWT Module

```xml
<configuration>
    <buildModules>
        <buildModule>
            <name>webclient</name>
            <sourceDirectories>
                <sourceDirectory>webclient/src/main/java</sourceDirectory>
            </sourceDirectories>
            <outputDirectory>webclient/target/classes</outputDirectory>
            <ecjCompile>
                <enabled>true</enabled>
                <source>21</source>
                <target>21</target>
            </ecjCompile>
            <gwtCompile>
                <enabled>true</enabled>
                <warDirectory>${rootDir}/GWT/war</warDirectory>
                <gwtModules>
                    <gwtModule>com.example.WebClient</gwtModule>
                </gwtModules>
            </gwtCompile>
        </buildModule>
    </buildModules>
</configuration>
```

### ECJ + CXF Module

```xml
<configuration>
    <buildModules>
        <buildModule>
            <name>webservice</name>
            <sourceDirectories>
                <sourceDirectory>webservice/src/main/java</sourceDirectory>
            </sourceDirectories>
            <outputDirectory>webservice/target/classes</outputDirectory>
            <ecjCompile>
                <enabled>true</enabled>
            </ecjCompile>
            <cxfCompile>
                <enabled>true</enabled>
                <serviceClass>com.example.OffsiteImpl</serviceClass>
                <outputDirectory>${rootDir}/GWT/war/WEB-INF/wsdl</outputDirectory>
                <outputFile>Offsite.wsdl</outputFile>
            </cxfCompile>
        </buildModule>
    </buildModules>
</configuration>
```

### Using Global Defaults

Global defaults reduce repetition when multiple modules share common settings. Module-specific values override the defaults.

```xml
<configuration>
    <defaultEcjCompile>
        <source>21</source>
        <target>21</target>
        <encoding>Cp1252</encoding>
        <propertiesFile>${installerHome}/EJC_Properties/org.eclipse.jdt.core.prefs21</propertiesFile>
        <nowarn>true</nowarn>
        <debug>false</debug>
        <compilerArguments>
            <arg>-XDignore.symbol.file</arg>
            <arg>-time</arg>
        </compilerArguments>
    </defaultEcjCompile>
    <defaultGwtCompile>
        <warDirectory>${rootDir}/GWT/war</warDirectory>
        <style>OBF</style>
        <logLevel>INFO</logLevel>
        <localWorkers>4</localWorkers>
    </defaultGwtCompile>

    <buildModules>
        <buildModule>
            <name>moduleA</name>
            <sourceDirectories>
                <sourceDirectory>module-a/src/main/java</sourceDirectory>
            </sourceDirectories>
            <outputDirectory>module-a/target/classes</outputDirectory>
            <ecjCompile>
                <enabled>true</enabled>
                <!-- inherits source, target, encoding, etc. from defaultEcjCompile -->
            </ecjCompile>
            <gwtCompile>
                <enabled>true</enabled>
                <gwtModules>
                    <gwtModule>com.example.ModuleA</gwtModule>
                </gwtModules>
                <!-- inherits warDirectory, style, etc. from defaultGwtCompile -->
            </gwtCompile>
        </buildModule>
        <buildModule>
            <name>moduleB</name>
            <sourceDirectories>
                <sourceDirectory>module-b/src/main/java</sourceDirectory>
            </sourceDirectories>
            <outputDirectory>module-b/target/classes</outputDirectory>
            <ecjCompile>
                <enabled>true</enabled>
                <source>17</source> <!-- overrides the default -->
                <target>17</target> <!-- overrides the default -->
            </ecjCompile>
        </buildModule>
    </buildModules>
</configuration>
```

### WAR Packaging

```xml
<buildModule>
    <name>webapp</name>
    <sourceDirectories>
        <sourceDirectory>webapp/src/main/java</sourceDirectory>
    </sourceDirectories>
    <outputDirectory>webapp/target/classes</outputDirectory>
    <ecjCompile>
        <enabled>true</enabled>
    </ecjCompile>
    <warPackage>
        <enabled>true</enabled>
        <warSourceDirectory>${rootDir}/GWT/war</warSourceDirectory>
        <warFile>${project.build.directory}/myapp.war</warFile>
        <includeClasses>true</includeClasses>
        <additionalContentDirectories>
            <directory>${rootDir}/GWT/war/extra</directory>
        </additionalContentDirectories>
        <libEntries>
            <lib>${project.basedir}/lib/extra.jar</lib>
        </libEntries>
    </warPackage>
</buildModule>
```

### Command Line

```bash
# Run module build
mvn classpath:module-build

# Skip module build
mvn classpath:module-build -Dmodule-build.skip=true
```
