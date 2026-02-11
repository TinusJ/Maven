# compile

Compiles Java sources using either javac or ECJ (Eclipse Compiler for Java) with support for multi-module projects and circular dependency resolution.

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
                        <goal>compile</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Parameters

| Parameter | Type | Property | Default | Description |
|-----------|------|----------|---------|-------------|
| `compiler` | `String` | `compiler.type` | `javac` | The compiler to use. Supported values: `javac`, `ecj`. |
| `source` | `String` | `compiler.source` | `${maven.compiler.source}` | The Java source version (e.g. `11`, `17`, `21`). |
| `target` | `String` | `compiler.target` | `${maven.compiler.target}` | The Java target version (e.g. `11`, `17`, `21`). |
| `propertiesFile` | `File` | `compiler.propertiesFile` | — | Path to an ECJ properties file. Only used when compiler is `ecj`. |
| `modules` | `List<CompilerModule>` | — | Single module from project | List of modules to compile. Each module defines its own source directories, resource directories, and output directory. |
| `showWarnings` | `boolean` | `compiler.showWarnings` | `false` | Whether to show compilation warnings. |
| `showDeprecation` | `boolean` | `compiler.showDeprecation` | `false` | Whether to show deprecation warnings. |
| `compilerArguments` | `List<String>` | `compiler.compilerArguments` | — | Additional compiler arguments to pass to the compiler. |

### CompilerModule Parameters

Each module in the `modules` list supports:

| Parameter | Type | Description |
|-----------|------|-------------|
| `sourceDirectories` | `List<String>` | Source directories for this module. |
| `resourceDirectories` | `List<String>` | Resource directories for this module. |
| `outputDirectory` | `File` | Output directory for compiled classes. |

## How It Works

1. **Module resolution** — If no modules are configured, a default single module is created from the project's compile source roots and build output directory.
2. **Sourcepath** — All modules' source directories are combined into a shared sourcepath, enabling circular dependency resolution between modules.
3. **Compilation** — Each module's source files are compiled independently into its own output directory, using either `javac` (via `javax.tools` API) or ECJ (via reflection).
4. **Classpath** — The full classpath includes all module output directories plus project compile dependencies.

## Examples

### Basic Compilation with javac

```xml
<configuration>
    <compiler>javac</compiler>
    <source>21</source>
    <target>21</target>
</configuration>
```

### ECJ Compilation with Properties File

```xml
<plugin>
    <groupId>com.tinusj.maven</groupId>
    <artifactId>classpath-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <configuration>
        <compiler>ecj</compiler>
        <propertiesFile>${project.basedir}/.settings/org.eclipse.jdt.core.prefs</propertiesFile>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.eclipse.jdt</groupId>
            <artifactId>ecj</artifactId>
            <version>3.13.101</version>
        </dependency>
    </dependencies>
</plugin>
```

### Multi-Module Compilation

When working with multiple source modules that may have circular dependencies:

```xml
<configuration>
    <modules>
        <module>
            <sourceDirectories>
                <sourceDirectory>module-a/src/main/java</sourceDirectory>
            </sourceDirectories>
            <resourceDirectories>
                <resourceDirectory>module-a/src/main/resources</resourceDirectory>
            </resourceDirectories>
            <outputDirectory>module-a/target/classes</outputDirectory>
        </module>
        <module>
            <sourceDirectories>
                <sourceDirectory>module-b/src/main/java</sourceDirectory>
            </sourceDirectories>
            <outputDirectory>module-b/target/classes</outputDirectory>
        </module>
    </modules>
</configuration>
```

### Command Line

```bash
# Compile with default settings
mvn classpath:compile

# Use ECJ compiler
mvn classpath:compile -Dcompiler.type=ecj

# Set source/target version
mvn classpath:compile -Dcompiler.source=21 -Dcompiler.target=21

# Show warnings
mvn classpath:compile -Dcompiler.showWarnings=true
```
