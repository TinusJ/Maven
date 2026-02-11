# test-compile

Compiles test Java sources using javac. This is the test-phase equivalent of the `compile` goal, compiling test source files and placing compiled classes in the test output directory.

**Goal prefix:** `classpath`
**Default phase:** `test-compile`
**Requires dependency resolution:** `test`

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
                        <goal>test-compile</goal>
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
| `source` | `String` | `testCompiler.source` | `${maven.compiler.source}` | The Java source version (e.g. `11`, `17`, `21`). |
| `target` | `String` | `testCompiler.target` | `${maven.compiler.target}` | The Java target version (e.g. `11`, `17`, `21`). |
| `showWarnings` | `boolean` | `testCompiler.showWarnings` | `false` | Whether to show compilation warnings. |
| `showDeprecation` | `boolean` | `testCompiler.showDeprecation` | `false` | Whether to show deprecation warnings. |
| `compilerArguments` | `List<String>` | `testCompiler.compilerArguments` | — | Additional compiler arguments to pass to javac. |
| `skip` | `boolean` | `testCompiler.skip` | `false` | Skip execution of this goal. |
| `maven.test.skip` | `boolean` | `maven.test.skip` | `false` | Standard Maven property to skip test compilation. |

## How It Works

1. **Source collection** — Collects all `.java` files from the project's test compile source roots.
2. **Classpath resolution** — Builds a test classpath that includes the project's compile output directory and all test-scoped dependencies.
3. **Compilation** — Compiles test source files using `javac` via the `javax.tools` API and places compiled classes in the test output directory.

## Examples

### Basic Test Compilation

```xml
<configuration>
    <source>21</source>
    <target>21</target>
</configuration>
```

### Show Warnings

```xml
<configuration>
    <showWarnings>true</showWarnings>
    <showDeprecation>true</showDeprecation>
</configuration>
```

### Additional Compiler Arguments

```xml
<configuration>
    <compilerArguments>
        <arg>-Xlint:all</arg>
        <arg>-parameters</arg>
    </compilerArguments>
</configuration>
```

### Command Line

```bash
# Compile test sources
mvn classpath:test-compile

# Set source/target version
mvn classpath:test-compile -DtestCompiler.source=21 -DtestCompiler.target=21

# Skip test compilation
mvn classpath:test-compile -DtestCompiler.skip=true

# Skip via standard Maven property
mvn classpath:test-compile -Dmaven.test.skip=true
```
