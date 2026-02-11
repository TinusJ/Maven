# generate-classpath

Collects source directories and resources into a single classpath file that can be used with Java's `-cp @file` syntax.

**Goal prefix:** `classpath`
**Default phase:** `generate-resources`

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
                        <goal>generate-classpath</goal>
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
| `sourceDirectories` | `List<String>` | `classpath.sourceDirectories` | Project compile source roots | Source directories to include in the classpath. |
| `resources` | `List<String>` | `classpath.resources` | Project resource directories | Resource directories to include in the classpath. |
| `outputFile` | `File` | `classpath.outputFile` | `${project.build.directory}/classpath.txt` | Output file path where the classpath will be written. |
| `includeDependencies` | `boolean` | `classpath.includeDependencies` | `false` | Whether to include project runtime dependencies in the classpath file. |

## How It Works

1. **Source directories** — The plugin collects all configured source directories (or the project's default compile source roots if none are specified). Only directories that exist on disk are included.
2. **Resource directories** — Resource directories are collected in the same way, defaulting to the project's configured resource directories.
3. **Dependencies** — When `includeDependencies` is set to `true`, all runtime classpath elements (JARs and directories) are added.
4. **Output** — All entries are written to the output file using the platform's path separator, making the file compatible with Java's `@file` classpath syntax.

## Examples

### Include Dependencies

```xml
<configuration>
    <includeDependencies>true</includeDependencies>
</configuration>
```

### Custom Output File

```xml
<configuration>
    <outputFile>${project.build.directory}/my-classpath.txt</outputFile>
</configuration>
```

### Custom Source and Resource Directories

```xml
<configuration>
    <sourceDirectories>
        <sourceDirectory>src/main/java</sourceDirectory>
        <sourceDirectory>src/generated/java</sourceDirectory>
    </sourceDirectories>
    <resources>
        <resource>src/main/resources</resource>
        <resource>src/main/config</resource>
    </resources>
</configuration>
```

### Command Line

```bash
# Generate classpath with default settings
mvn classpath:generate-classpath

# Include dependencies
mvn classpath:generate-classpath -Dclasspath.includeDependencies=true
```
