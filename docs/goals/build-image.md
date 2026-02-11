# build-image

Packages an application into an OCI Docker image using Cloud Native Buildpacks. Works with any Java application — no Spring Boot dependency required.

**Goal prefix:** `classpath`
**Default phase:** `package`
**Requires dependency resolution:** `compile+runtime`
**Thread safe:** Yes

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
                        <goal>build-image</goal>
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
| `imageName` | `String` | `build-image.imageName` | `${project.artifactId}:${project.version}` | Name of the image to build. |
| `builder` | `String` | `build-image.builder` | — | Builder image to use (e.g. `paketobuildpacks/builder-jammy-base:latest`). |
| `runImage` | `String` | `build-image.runImage` | — | Run image to use as the base for the application image. |
| `env` | `Map<String, String>` | — | — | Environment variables to pass to the buildpacks. |
| `cleanCache` | `Boolean` | `build-image.cleanCache` | `false` | Whether to clean the cache before building. |
| `pullPolicy` | `PullPolicy` | `build-image.pullPolicy` | — | Image pull policy: `IF_NOT_PRESENT`, `ALWAYS`, or `NEVER`. |
| `publish` | `Boolean` | `build-image.publish` | `false` | Whether to publish the image to a registry after building. |
| `network` | `String` | `build-image.network` | — | Network mode for the build container. |
| `imagePlatform` | `String` | `build-image.imagePlatform` | — | Target platform for the image (e.g. `linux/amd64`). |
| `tags` | `List<String>` | — | — | Additional tags to apply to the built image. |
| `bindings` | `List<String>` | — | — | Volume bindings for the build container. |
| `buildpacks` | `List<String>` | — | — | Custom buildpacks to use instead of the builder's defaults. |
| `trustBuilder` | `Boolean` | `build-image.trustBuilder` | `false` | Whether to trust the builder. |
| `createdDate` | `String` | `build-image.createdDate` | — | Created date for the image in ISO 8601 format. |
| `applicationDirectory` | `String` | `build-image.applicationDirectory` | — | Application directory inside the image. |
| `skip` | `boolean` | `build-image.skip` | `false` | Skip execution of the build-image goal. |
| `classifier` | `String` | — | — | Optional classifier for the archive artifact. |
| `docker` | `DockerConfiguration` | — | — | Docker connection and registry configuration (see below). |

## Docker Configuration

The `docker` parameter configures how the plugin connects to Docker and authenticates with registries.

| Parameter | Type | Description |
|-----------|------|-------------|
| `host` | `String` | Docker daemon host URL (e.g. `tcp://localhost:2376`). Mutually exclusive with `context`. |
| `context` | `String` | Docker context name to use. Mutually exclusive with `host`. |
| `tlsVerify` | `boolean` | Whether to verify TLS certificates when connecting. |
| `certPath` | `String` | Path to TLS certificate directory (ca.pem, cert.pem, key.pem). |
| `bindHostToBuilder` | `boolean` | Whether to bind the host information to the builder container. |
| `builderRegistry` | `RegistryAuth` | Authentication for the builder image registry. |
| `publishRegistry` | `RegistryAuth` | Authentication for the publish registry. |

### Registry Authentication

Each registry (`builderRegistry`, `publishRegistry`) supports either username/password or token authentication:

| Parameter | Type | Description |
|-----------|------|-------------|
| `username` | `String` | Registry username. |
| `password` | `String` | Registry password. |
| `url` | `String` | Registry URL. |
| `email` | `String` | Registry email. |
| `token` | `String` | Registry authentication token (alternative to username/password). |

> **Note:** `username`/`password` and `token` are mutually exclusive authentication methods.

## Examples

### Basic Image Build

```xml
<configuration>
    <imageName>myapp:1.0.0</imageName>
</configuration>
```

### Advanced Configuration

```xml
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
```

### Docker Daemon Configuration

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

### Command Line

```bash
# Build image with default settings
mvn classpath:build-image

# Build image with custom name
mvn classpath:build-image -Dbuild-image.imageName=myapp:latest

# Build and publish to a registry
mvn classpath:build-image -Dbuild-image.publish=true

# Use a specific builder
mvn classpath:build-image -Dbuild-image.builder=paketobuildpacks/builder-jammy-base:latest

# Skip image build
mvn classpath:build-image -Dbuild-image.skip=true

# Target a specific platform
mvn classpath:build-image -Dbuild-image.imagePlatform=linux/amd64
```
