# java-process

Executes a forked Java process with a configurable classpath, JVM arguments, and program arguments. Useful for running tools like GWT compilers, CXF WSDL generators, or any Java main class as part of the build.

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
                        <goal>java-process</goal>
                    </goals>
                    <configuration>
                        <mainClass>com.example.MyTool</mainClass>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Parameters

| Parameter | Type | Property | Default | Description |
|-----------|------|----------|---------|-------------|
| `mainClass` | `String` | `javaprocess.mainClass` | — | **(Required)** The fully qualified main class to execute. |
| `classpathEntries` | `List<String>` | — | — | Additional classpath entries for the forked process. |
| `includeProjectClasspath` | `boolean` | `javaprocess.includeProjectClasspath` | `true` | Whether to include the project's compile classpath. |
| `jvmArguments` | `List<String>` | — | — | JVM arguments for the forked process (e.g. `-Xmx512m`). |
| `systemProperties` | `Map<String, String>` | — | — | System properties passed as `-Dkey=value` JVM arguments. |
| `arguments` | `List<String>` | — | — | Program arguments passed to the main class. |
| `workingDirectory` | `File` | `javaprocess.workingDirectory` | `${project.basedir}` | Working directory for the forked process. |
| `failOnError` | `boolean` | `javaprocess.failOnError` | `true` | Whether to fail the build on a non-zero exit code. |
| `skip` | `boolean` | `javaprocess.skip` | `false` | Skip execution of this goal. |

## How It Works

1. The plugin resolves the `java` executable from `java.home`.
2. A classpath is built by combining project compile dependencies (when `includeProjectClasspath` is `true`) with any configured `classpathEntries`.
3. The Java process is forked with the specified JVM arguments, system properties, main class, and program arguments.
4. Standard output and error from the process are streamed to the Maven log.
5. The build fails if the process exits with a non-zero code and `failOnError` is `true`.

## Examples

### GWT Compilation

```xml
<configuration>
    <mainClass>com.google.gwt.dev.Compiler</mainClass>
    <classpathEntries>
        <entry>${project.basedir}/lib/gwt-dev.jar</entry>
        <entry>${project.basedir}/lib/gwt-user.jar</entry>
    </classpathEntries>
    <jvmArguments>
        <arg>-Djava.io.tmpdir=${webtempfolder}</arg>
    </jvmArguments>
    <arguments>
        <arg>-failOnError</arg>
        <arg>-war</arg>
        <arg>${rootDir}/GWT/war</arg>
        <arg>-strict</arg>
        <arg>-style</arg>
        <arg>OBF</arg>
        <arg>com.example.MyModule</arg>
    </arguments>
</configuration>
```

### CXF WSDL Generation

```xml
<configuration>
    <mainClass>org.apache.cxf.tools.java2ws.JavaToWS</mainClass>
    <classpathEntries>
        <entry>${project.basedir}/lib/cxf-tools.jar</entry>
    </classpathEntries>
    <arguments>
        <arg>-wsdl</arg>
        <arg>-createxsdimports</arg>
        <arg>-d</arg>
        <arg>${rootDir}/GWT/war/offsitewebservice/WEB-INF/wsdl</arg>
        <arg>-o</arg>
        <arg>Offsite.wsdl</arg>
        <arg>com.example.OffsiteImpl</arg>
    </arguments>
</configuration>
```

### Custom JVM Settings

```xml
<configuration>
    <mainClass>com.example.MyTool</mainClass>
    <jvmArguments>
        <arg>-Xmx1g</arg>
        <arg>-XX:+UseG1GC</arg>
    </jvmArguments>
    <systemProperties>
        <app.env>production</app.env>
        <app.config>/etc/myapp/config.properties</app.config>
    </systemProperties>
    <workingDirectory>${project.build.directory}</workingDirectory>
</configuration>
```

### Command Line

```bash
# Run a Java process
mvn classpath:java-process -Djavaprocess.mainClass=com.example.MyTool

# Skip execution
mvn classpath:java-process -Djavaprocess.skip=true
```
