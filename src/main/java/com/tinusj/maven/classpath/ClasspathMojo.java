package com.tinusj.maven.classpath;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven Mojo that combines source directories and resources into a single classpath file.
 * This file can be used with the Java -cp @file syntax for specifying classpaths.
 */
@Mojo(name = "generate-classpath", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ClasspathMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * List of source directories to include in the classpath.
     * Defaults to the project's compile source roots.
     */
    @Parameter(property = "classpath.sourceDirectories")
    private List<String> sourceDirectories;

    /**
     * List of resource directories to include in the classpath.
     * Defaults to the project's resource directories.
     */
    @Parameter(property = "classpath.resources")
    private List<String> resources;

    /**
     * Output file where the classpath will be written.
     * Default is ${project.build.directory}/classpath.txt
     */
    @Parameter(property = "classpath.outputFile", 
               defaultValue = "${project.build.directory}/classpath.txt")
    private File outputFile;

    /**
     * Whether to include project dependencies in the classpath file.
     * When enabled, uses runtime classpath elements which include all dependencies
     * needed to run the application.
     */
    @Parameter(property = "classpath.includeDependencies", defaultValue = "false")
    private boolean includeDependencies;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<String> classpathEntries = new ArrayList<>();

        // Add source directories
        if (sourceDirectories == null || sourceDirectories.isEmpty()) {
            // Use default compile source roots
            sourceDirectories = project.getCompileSourceRoots();
        }
        
        if (sourceDirectories != null) {
            for (String sourceDir : sourceDirectories) {
                File dir = new File(sourceDir);
                if (dir.exists()) {
                    classpathEntries.add(dir.getAbsolutePath());
                    getLog().info("Added source directory: " + dir.getAbsolutePath());
                } else {
                    getLog().warn("Source directory does not exist: " + sourceDir);
                }
            }
        }

        // Add resource directories
        if (resources == null || resources.isEmpty()) {
            // Use default resource directories
            resources = new ArrayList<>();
            if (project.getBuild() != null && project.getBuild().getResources() != null) {
                project.getBuild().getResources().forEach(resource -> 
                    resources.add(resource.getDirectory())
                );
            }
        }
        
        if (resources != null) {
            for (String resourceDir : resources) {
                File dir = new File(resourceDir);
                if (dir.exists()) {
                    classpathEntries.add(dir.getAbsolutePath());
                    getLog().info("Added resource directory: " + dir.getAbsolutePath());
                } else {
                    getLog().warn("Resource directory does not exist: " + resourceDir);
                }
            }
        }

        // Add dependencies if requested
        if (includeDependencies) {
            try {
                List<String> classpathElements = project.getRuntimeClasspathElements();
                for (String element : classpathElements) {
                    File file = new File(element);
                    if (file.exists()) {
                        classpathEntries.add(file.getAbsolutePath());
                        getLog().debug("Added dependency: " + file.getAbsolutePath());
                    }
                }
            } catch (DependencyResolutionRequiredException e) {
                throw new MojoExecutionException("Failed to resolve runtime classpath elements", e);
            }
        }

        // Create output directory if it doesn't exist
        if (outputFile.getParentFile() != null && !outputFile.getParentFile().exists()) {
            if (!outputFile.getParentFile().mkdirs()) {
                throw new MojoExecutionException("Failed to create output directory: " 
                    + outputFile.getParentFile().getAbsolutePath());
            }
        }

        // Write classpath entries to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            ClassPath classPath = ClassPath.of(classpathEntries);
            writer.write(classPath.toString());
            getLog().info("Classpath file written to: " + outputFile.getAbsolutePath());
            getLog().info("Total classpath entries: " + classpathEntries.size());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write classpath file: " 
                + outputFile.getAbsolutePath(), e);
        }
    }
}
