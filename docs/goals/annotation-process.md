# annotation-process

Runs Java annotation processing on source files to generate additional source code or resources. Uses the standard `javax.tools.JavaCompiler` API with `-proc:only` to execute annotation processors without compiling the source files.

**Goal prefix:** `classpath`
**Default phase:** `generate-sources`
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
                        <goal>annotation-process</goal>
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
| `source` | `String` | `apt.source` | `${maven.compiler.source}` | The Java source version (e.g. `11`, `17`, `21`). |
| `target` | `String` | `apt.target` | `${maven.compiler.target}` | The Java target version (e.g. `11`, `17`, `21`). |
| `annotationProcessors` | `List<String>` | `apt.processors` | — | Fully qualified annotation processor class names. If not specified, processors are discovered via `META-INF/services`. |
| `processorPathEntries` | `List<String>` | — | — | Classpath entries for annotation processor discovery and loading. |
| `generatedSourcesDirectory` | `File` | `apt.generatedSourcesDirectory` | `${project.build.directory}/generated-sources/apt` | Output directory for generated source files. |
| `showWarnings` | `boolean` | `apt.showWarnings` | `false` | Whether to show compilation warnings. |
| `compilerArguments` | `List<String>` | `apt.compilerArguments` | — | Additional compiler arguments. |
| `skip` | `boolean` | `apt.skip` | `false` | Skip execution of this goal. |

## How It Works

1. **Source collection** — Collects all `.java` files from the project's compile source roots.
2. **Processor discovery** — If `annotationProcessors` is specified, those processors are used. Otherwise, processors are discovered from the `processorPath` via the standard `META-INF/services/javax.annotation.processing.Processor` mechanism.
3. **Processing** — Runs `javac` with `-proc:only` to execute annotation processors without compiling the source files.
4. **Source registration** — The generated sources directory is added as a compile source root so generated files are included in subsequent compilation.

## Examples

### Explicit Processor

```xml
<configuration>
    <annotationProcessors>
        <processor>com.example.MyAnnotationProcessor</processor>
    </annotationProcessors>
    <processorPathEntries>
        <entry>${project.basedir}/lib/my-processor.jar</entry>
    </processorPathEntries>
</configuration>
```

### Custom Generated Sources Directory

```xml
<configuration>
    <generatedSourcesDirectory>${project.build.directory}/generated-sources/custom</generatedSourcesDirectory>
</configuration>
```

### Passing Processor Options

Annotation processor options can be passed as compiler arguments using the `-A` prefix:

```xml
<configuration>
    <compilerArguments>
        <arg>-Acom.example.debug=true</arg>
        <arg>-Acom.example.outputFormat=json</arg>
    </compilerArguments>
</configuration>
```

### Command Line

```bash
# Run annotation processing
mvn classpath:annotation-process

# Skip annotation processing
mvn classpath:annotation-process -Dapt.skip=true
```
