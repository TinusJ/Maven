# Maven Classpath Plugin

A Maven plugin that provides utilities for Java project builds, including classpath generation, custom compilation, forked Java process execution, multi-module builds, and Docker image building.

## Features

- **Generate Classpath** — Collects source directories and resources into a single classpath file compatible with Java's `-cp @file` syntax.
- **Custom Compilation** — Compiles Java sources using javac or ECJ (Eclipse Compiler for Java) with multi-module and circular dependency support.
- **Annotation Processing** — Runs Java annotation processors on source files to generate additional source code or resources.
- **Test Compilation** — Compiles test Java sources using javac with test classpath resolution.
- **Java Process Execution** — Runs any Java main class as a forked process with full control over classpath, JVM arguments, and program arguments.
- **Multi-Module Build** — Orchestrates complex builds with per-module ECJ compilation, GWT compilation, CXF WSDL generation, and WAR packaging.
- **Docker Image Building** — Packages applications into OCI Docker images using Cloud Native Buildpacks.

## Requirements

- Java 21 or later
- Maven 3.8.1 or later

## Quick Start

Add the plugin to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.tinusj.maven</groupId>
            <artifactId>classpath-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
        </plugin>
    </plugins>
</build>
```

## Building the Plugin

```bash
mvn clean install
```

## Goals

The plugin provides seven goals. Click each link for full documentation including all parameters, configuration options, and examples.

| Goal | Default Phase | Description |
|------|---------------|-------------|
| [`generate-classpath`](docs/goals/generate-classpath.md) | `generate-resources` | Collects source directories and resources into a classpath file. |
| [`compile`](docs/goals/compile.md) | `compile` | Compiles Java sources using javac or ECJ with multi-module support. |
| [`annotation-process`](docs/goals/annotation-process.md) | `generate-sources` | Runs annotation processors on source files to generate additional sources. |
| [`test-compile`](docs/goals/test-compile.md) | `test-compile` | Compiles test Java sources using javac. |
| [`java-process`](docs/goals/java-process.md) | `compile` | Executes a forked Java process (e.g. GWT compiler, CXF tools). |
| [`module-build`](docs/goals/module-build.md) | `compile` | Processes multiple modules with ECJ, GWT, CXF, and WAR packaging steps. |
| [`build-image`](docs/goals/build-image.md) | `package` | Builds an OCI Docker image using Cloud Native Buildpacks. |

### generate-classpath

Collects source directories and resources into a single classpath file that can be used with Java's `-cp @file` syntax.

```bash
mvn classpath:generate-classpath
```

See [full documentation](docs/goals/generate-classpath.md) for all parameters and examples.

### compile

Compiles Java sources using either javac or ECJ with support for multi-module projects and circular dependency resolution.

```bash
mvn classpath:compile
```

See [full documentation](docs/goals/compile.md) for all parameters and examples.

### annotation-process

Runs Java annotation processors on source files to generate additional source code or resources. Processors are discovered automatically or specified explicitly.

```bash
mvn classpath:annotation-process
```

See [full documentation](docs/goals/annotation-process.md) for all parameters and examples.

### test-compile

Compiles test Java sources using javac with test classpath resolution. This is the test-phase equivalent of the `compile` goal.

```bash
mvn classpath:test-compile
```

See [full documentation](docs/goals/test-compile.md) for all parameters and examples.

### java-process

Executes a forked Java process with configurable classpath, JVM arguments, and program arguments. Useful for running GWT compilers, CXF WSDL generators, or any Java main class during the build.

```bash
mvn classpath:java-process -Djavaprocess.mainClass=com.example.MyTool
```

See [full documentation](docs/goals/java-process.md) for all parameters and examples.

### module-build

Processes multiple build modules, each with independently configurable ECJ compilation, GWT compilation, CXF WSDL generation, and WAR packaging. Supports global defaults with per-module overrides.

```bash
mvn classpath:module-build
```

See [full documentation](docs/goals/module-build.md) for all parameters and examples.

### build-image

Packages an application into an OCI Docker image using Cloud Native Buildpacks. Works with any Java application — no Spring Boot dependency required.

```bash
mvn classpath:build-image
```

See [full documentation](docs/goals/build-image.md) for all parameters and examples.

## License

This project is open source.