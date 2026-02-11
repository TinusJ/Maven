package com.tinusj.maven.classpath;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.springframework.boot.buildpack.platform.build.BuildRequest;
import org.springframework.boot.buildpack.platform.build.Builder;
import org.springframework.boot.buildpack.platform.build.BuilderException;
import org.springframework.boot.buildpack.platform.build.BuildpackReference;
import org.springframework.boot.buildpack.platform.build.PullPolicy;
import org.springframework.boot.buildpack.platform.docker.type.Binding;
import org.springframework.boot.buildpack.platform.docker.type.ImageName;
import org.springframework.boot.buildpack.platform.docker.type.ImageReference;
import org.springframework.boot.buildpack.platform.io.Owner;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Maven Mojo that packages an application into an OCI Docker image using Cloud Native Buildpacks.
 */
@Mojo(name = "build-image", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true,
        threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildImageMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Skip the execution of the build-image goal.
     */
    @Parameter(property = "build-image.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Name of the image to build. Defaults to artifactId:version.
     */
    @Parameter(property = "build-image.imageName")
    private String imageName;

    /**
     * Builder image to use for building.
     */
    @Parameter(property = "build-image.builder")
    private String builder;

    /**
     * Run image to use as the base for the application image.
     */
    @Parameter(property = "build-image.runImage")
    private String runImage;

    /**
     * Environment variables to pass to the buildpacks.
     */
    @Parameter
    private Map<String, String> env;

    /**
     * Whether to clean the cache before building.
     */
    @Parameter(property = "build-image.cleanCache")
    private Boolean cleanCache;

    /**
     * Image pull policy.
     */
    @Parameter(property = "build-image.pullPolicy")
    private PullPolicy pullPolicy;

    /**
     * Whether to publish the image to a registry.
     */
    @Parameter(property = "build-image.publish")
    private Boolean publish;

    /**
     * Network mode for the build container.
     */
    @Parameter(property = "build-image.network")
    private String network;

    /**
     * Platform for the image (e.g., "linux/amd64").
     */
    @Parameter(property = "build-image.imagePlatform")
    private String imagePlatform;

    /**
     * Additional tags to apply to the built image.
     */
    @Parameter
    private List<String> tags;

    /**
     * Volume bindings for the build container.
     */
    @Parameter
    private List<String> bindings;

    /**
     * Custom buildpacks to use.
     */
    @Parameter
    private List<String> buildpacks;

    /**
     * Whether to trust the builder.
     */
    @Parameter(property = "build-image.trustBuilder")
    private Boolean trustBuilder;

    /**
     * Created date for the image (ISO 8601 format).
     */
    @Parameter(property = "build-image.createdDate")
    private String createdDate;

    /**
     * Application directory in the image.
     */
    @Parameter(property = "build-image.applicationDirectory")
    private String applicationDirectory;

    /**
     * Directory containing the archive to package.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File sourceDirectory;

    /**
     * Name of the final artifact without extension.
     */
    @Parameter(defaultValue = "${project.build.finalName}", readonly = true, required = true)
    private String finalName;

    /**
     * Optional classifier for the artifact.
     */
    @Parameter
    private String classifier;

    /**
     * Docker configuration.
     */
    @Parameter
    private MavenDockerConfiguration docker;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Skip if packaging is pom or skip flag is set
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Skipping build-image for pom packaging");
            return;
        }

        if (skip) {
            getLog().info("Skipping build-image (skip=true)");
            return;
        }

        // Find the archive file
        File archiveFile = findArchiveFile();
        if (!archiveFile.exists()) {
            throw new MojoExecutionException("Archive file not found: " + archiveFile.getAbsolutePath());
        }

        getLog().info("Building image from: " + archiveFile.getAbsolutePath());

        try {
            // Create the build request
            BuildRequest request = createBuildRequest(archiveFile);

            // Create and configure the builder
            Builder builder = createBuilder();

            // Execute the build
            builder.build(request);

            getLog().info("Successfully built image: " + getImageName());
        } catch (BuilderException | IOException e) {
            throw new MojoExecutionException("Failed to build image", e);
        }
    }

    private File findArchiveFile() throws MojoExecutionException {
        String packaging = project.getPackaging();
        String extension;

        switch (packaging) {
            case "jar":
            case "war":
            case "ear":
                extension = packaging;
                break;
            default:
                // Try jar as default
                extension = "jar";
        }

        String fileName = finalName;
        if (classifier != null && !classifier.isEmpty()) {
            fileName += "-" + classifier;
        }
        fileName += "." + extension;

        return new File(sourceDirectory, fileName);
    }

    private BuildRequest createBuildRequest(File archiveFile) throws IOException {
        ImageReference imageRef = ImageReference.of(getImageName());

        BuildRequest request = BuildRequest.of(imageRef, (owner) -> {
            try {
                return new PackagedTarArchive(archiveFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create tar archive from " + archiveFile, e);
            }
        });

        // Configure builder
        if (builder != null && !builder.isEmpty()) {
            request = request.withBuilder(ImageReference.of(builder));
        }

        // Configure run image
        if (runImage != null && !runImage.isEmpty()) {
            request = request.withRunImage(ImageReference.of(runImage));
        }

        // Configure environment
        if (env != null && !env.isEmpty()) {
            request = request.withEnv(env);
        }

        // Configure clean cache
        if (cleanCache != null && cleanCache) {
            request = request.withCleanCache(true);
        }

        // Configure pull policy
        if (pullPolicy != null) {
            request = request.withPullPolicy(pullPolicy);
        }

        // Configure publish
        if (publish != null && publish) {
            request = request.withPublish(true);
        }

        // Configure network
        if (network != null && !network.isEmpty()) {
            request = request.withNetwork(network);
        }

        // Configure tags
        if (tags != null && !tags.isEmpty()) {
            ImageReference[] tagRefs = tags.stream()
                    .map(ImageReference::of)
                    .toArray(ImageReference[]::new);
            request = request.withTags(tagRefs);
        }

        // Configure bindings
        if (bindings != null && !bindings.isEmpty()) {
            Binding[] bindingArray = bindings.stream()
                    .map(Binding::of)
                    .toArray(Binding[]::new);
            request = request.withBindings(bindingArray);
        }

        // Configure buildpacks
        if (buildpacks != null && !buildpacks.isEmpty()) {
            BuildpackReference[] buildpackRefs = buildpacks.stream()
                    .map(BuildpackReference::of)
                    .toArray(BuildpackReference[]::new);
            request = request.withBuildpacks(buildpackRefs);
        }

        // Configure trust builder
        if (trustBuilder != null && trustBuilder) {
            request = request.withTrustBuilder(true);
        }

        // Configure created date
        if (createdDate != null && !createdDate.isEmpty()) {
            request = request.withCreatedDate(createdDate);
        }

        // Configure application directory
        if (applicationDirectory != null && !applicationDirectory.isEmpty()) {
            request = request.withApplicationDirectory(applicationDirectory);
        }

        // Configure image platform
        if (imagePlatform != null && !imagePlatform.isEmpty()) {
            request = request.withImagePlatform(imagePlatform);
        }

        return request;
    }

    private Builder createBuilder() {
        MojoBuildLog buildLog = new MojoBuildLog(this::getLog);

        if (docker != null) {
            return new Builder(buildLog, docker.asDockerConfiguration());
        } else {
            return new Builder(buildLog);
        }
    }

    private String getImageName() {
        if (imageName != null && !imageName.isEmpty()) {
            return imageName;
        }
        return project.getArtifactId() + ":" + project.getVersion();
    }
}
