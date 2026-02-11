# Maven Classpath Plugin

A Maven plugin that provides utilities for Java project builds, including classpath generation, custom compilation, and Docker image building.

## Features

- **Generate Classpath**: Collects source directories and resources into a single classpath file
- **Custom Compilation**: Compiles Java sources using javac or ECJ with module support
- **Build Docker Images**: Packages applications into OCI Docker images using Cloud Native Buildpacks

## Requirements

- Java 21 or later
- Maven 3.8.1 or later

## Building the Plugin

```bash
mvn clean install
```

## Goals

### 1. `generate-classpath`

Collects source directories and resources into a single classpath file that can be used with Java's `-cp @file` syntax.

#### Usage

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

#### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sourceDirectories` | List<String> | project compile source roots | Source directories to include in the classpath |
| `resources` | List<String> | project resource directories | Resource directories to include in the classpath |
| `outputFile` | File | `${project.build.directory}/classpath.txt` | Output file path |
| `includeDependencies` | boolean | false | Whether to include project dependencies |

### 2. `compile`

Compiles Java sources using either javac or ECJ (Eclipse Compiler for Java) with support for multi-module projects.

See existing documentation for usage details.

### 3. `build-image` (NEW)

Packages an application into an OCI Docker image using Cloud Native Buildpacks, similar to Spring Boot's build-image goal but works with any Java application.

#### Basic Usage

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
                        <goal>build-image</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

#### Advanced Configuration

```xml
<plugin>
    <groupId>com.tinusj.maven</groupId>
    <artifactId>classpath-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>build-image</goal>
            </goals>
            <configuration>
                <imageName>myapp:1.0.0</imageName>
                <builder>paketobuildpacks/builder-jammy-base:latest</builder>
                <env>
                    <BP_JVM_VERSION>21</BP_JVM_VERSION>
                    <BP_JVM_JLINK_ENABLED>false</BP_JVM_JLINK_ENABLED>
                </env>
                <docker>
                    <publishRegistry>
                        <username>${env.REGISTRY_USER}</username>
                        <password>${env.REGISTRY_PASSWORD}</password>
                        <url>https://registry.example.com</url>
                    </publishRegistry>
                </docker>
                <tags>
                    <tag>myapp:latest</tag>
                </tags>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Parameters

| Parameter | Type | Property | Default | Description |
|-----------|------|----------|---------|-------------|
| `imageName` | String | `build-image.imageName` | `${project.artifactId}:${project.version}` | Name of the image to build |
| `builder` | String | `build-image.builder` | - | Builder image to use (e.g., paketobuildpacks/builder-jammy-base) |
| `runImage` | String | `build-image.runImage` | - | Run image to use as base for the application image |
| `env` | Map<String, String> | - | - | Environment variables to pass to buildpacks |
| `cleanCache` | Boolean | `build-image.cleanCache` | false | Whether to clean the cache before building |
| `pullPolicy` | PullPolicy | `build-image.pullPolicy` | - | Image pull policy (IF_NOT_PRESENT, ALWAYS, NEVER) |
| `publish` | Boolean | `build-image.publish` | false | Whether to publish the image to a registry |
| `network` | String | `build-image.network` | - | Network mode for the build container |
| `imagePlatform` | String | `build-image.imagePlatform` | - | Platform for the image (e.g., linux/amd64) |
| `tags` | List<String> | - | - | Additional tags to apply to the built image |
| `bindings` | List<String> | - | - | Volume bindings for the build container |
| `buildpacks` | List<String> | - | - | Custom buildpacks to use |
| `trustBuilder` | Boolean | `build-image.trustBuilder` | false | Whether to trust the builder |
| `createdDate` | String | `build-image.createdDate` | - | Created date for the image (ISO 8601 format) |
| `applicationDirectory` | String | `build-image.applicationDirectory` | - | Application directory in the image |
| `skip` | boolean | `build-image.skip` | false | Skip the execution of the build-image goal |
| `docker` | DockerConfiguration | - | - | Docker connection and registry configuration |

#### Docker Configuration

The `docker` parameter supports the following nested configuration:

```xml
<docker>
    <host>tcp://localhost:2376</host>
    <tlsVerify>true</tlsVerify>
    <certPath>/path/to/certs</certPath>
    <bindHostToBuilder>false</bindHostToBuilder>
    <builderRegistry>
        <username>user</username>
        <password>pass</password>
        <url>https://registry.example.com</url>
        <email>user@example.com</email>
    </builderRegistry>
    <publishRegistry>
        <token>my-token</token>
    </publishRegistry>
</docker>
```

**Note**: `host` and `context` are mutually exclusive. Registry auth can use either `username`/`password` or `token`.

#### Running from Command Line

```bash
# Build image with default settings
mvn classpath:build-image

# Build image with custom name
mvn classpath:build-image -Dbuild-image.imageName=myapp:latest

# Build and publish
mvn classpath:build-image -Dbuild-image.publish=true

# Use a specific builder
mvn classpath:build-image -Dbuild-image.builder=paketobuildpacks/builder-jammy-base:latest
```

## License

This project is open source.