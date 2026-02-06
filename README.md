# Maven Classpath Plugin

A Maven plugin that combines source directories and resources into a single classpath file. This file can be used with Java's `-cp @file` syntax for specifying classpaths in later builds.

## Features

- Collects source directories (defaults to project compile source roots)
- Collects resource directories (defaults to project resource directories)
- Optionally includes project dependencies
- Writes all paths to a single file, separated by the platform's path separator
- Can be used with `java -cp @file` or `javac -cp @file` commands

## Building the Plugin

```bash
mvn clean install
```

## Usage

### Basic Configuration

Add the plugin to your project's `pom.xml`:

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

### Running the Plugin

```bash
mvn classpath:generate-classpath
```

Or as part of the build lifecycle:

```bash
mvn generate-resources
```

The plugin will create a classpath file at `target/classpath.txt` by default.

### Using the Generated Classpath File

Once the classpath file is generated, you can use it with Java commands:

```bash
# Compile with the classpath
javac -cp @target/classpath.txt src/main/java/com/example/MyClass.java

# Run with the classpath
java -cp @target/classpath.txt com.example.MyClass
```

## Configuration

### Custom Source Directories

```xml
<configuration>
    <sourceDirectories>
        <sourceDirectory>src/main/java</sourceDirectory>
        <sourceDirectory>src/generated/java</sourceDirectory>
    </sourceDirectories>
</configuration>
```

### Custom Resource Directories

```xml
<configuration>
    <resources>
        <resource>src/main/resources</resource>
        <resource>src/config</resource>
    </resources>
</configuration>
```

### Custom Output File

```xml
<configuration>
    <outputFile>${project.build.directory}/my-classpath.txt</outputFile>
</configuration>
```

### Include Dependencies

```xml
<configuration>
    <includeDependencies>true</includeDependencies>
</configuration>
```

### Complete Example

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.tinusj.maven</groupId>
            <artifactId>classpath-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <phase>generate-resources</phase>
                    <goals>
                        <goal>generate-classpath</goal>
                    </goals>
                    <configuration>
                        <sourceDirectories>
                            <sourceDirectory>src/main/java</sourceDirectory>
                        </sourceDirectories>
                        <resources>
                            <resource>src/main/resources</resource>
                        </resources>
                        <outputFile>${project.build.directory}/classpath.txt</outputFile>
                        <includeDependencies>false</includeDependencies>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Goal: generate-classpath

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sourceDirectories` | List<String> | project compile source roots | Source directories to include in the classpath |
| `resources` | List<String> | project resource directories | Resource directories to include in the classpath |
| `outputFile` | File | `${project.build.directory}/classpath.txt` | Output file path |
| `includeDependencies` | boolean | false | Whether to include project dependencies |

## License

This project is open source.