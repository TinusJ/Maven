package com.tinusj.maven.classpath;

import org.springframework.boot.buildpack.platform.io.Owner;
import org.springframework.boot.buildpack.platform.io.TarArchive;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * TarArchive implementation that converts a JAR/WAR file to tar format for buildpack consumption.
 */
public class PackagedTarArchive implements TarArchive {

    private final TarArchive delegate;

    /**
     * Creates a new PackagedTarArchive from the given JAR/WAR file.
     *
     * @param archiveFile the JAR or WAR file to convert
     * @throws IOException if the archive cannot be read
     */
    public PackagedTarArchive(File archiveFile) throws IOException {
        // Use the standard CNB user/group IDs (1000/1000)
        Owner owner = Owner.of(1000, 1000);
        this.delegate = TarArchive.fromZip(archiveFile, owner);
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        delegate.writeTo(outputStream);
    }
}
