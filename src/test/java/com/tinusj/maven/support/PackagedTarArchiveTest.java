package com.tinusj.maven.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for PackagedTarArchive.
 */
public class PackagedTarArchiveTest {

    @TempDir
    File tempDir;

    @Test
    public void testCreateFromJarFile() throws IOException {
        // Create a temporary JAR file
        File jarFile = new File(tempDir, "test.jar");
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            // Add a file entry
            JarEntry fileEntry = new JarEntry("META-INF/MANIFEST.MF");
            jarOut.putNextEntry(fileEntry);
            jarOut.write("Manifest-Version: 1.0\n".getBytes());
            jarOut.closeEntry();

            // Add another file entry
            JarEntry classEntry = new JarEntry("com/example/Main.class");
            jarOut.putNextEntry(classEntry);
            jarOut.write(new byte[100]); // Dummy class data
            jarOut.closeEntry();

            // Add a directory entry
            JarEntry dirEntry = new JarEntry("com/example/");
            jarOut.putNextEntry(dirEntry);
            jarOut.closeEntry();
        }

        // Create PackagedTarArchive from the JAR
        PackagedTarArchive tarArchive = new PackagedTarArchive(jarFile);
        assertNotNull(tarArchive);

        // Write the tar archive to a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tarArchive.writeTo(outputStream);

        // Verify that output was generated
        byte[] tarData = outputStream.toByteArray();
        assertTrue(tarData.length > 0, "Tar archive should have data");
    }

    @Test
    public void testCreateFromWarFile() throws IOException {
        // Create a temporary WAR file (WAR is just a JAR with different structure)
        File warFile = new File(tempDir, "test.war");
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(warFile))) {
            // Add typical WAR entries
            JarEntry webInfEntry = new JarEntry("WEB-INF/");
            jarOut.putNextEntry(webInfEntry);
            jarOut.closeEntry();

            JarEntry webXmlEntry = new JarEntry("WEB-INF/web.xml");
            jarOut.putNextEntry(webXmlEntry);
            jarOut.write("<web-app></web-app>".getBytes());
            jarOut.closeEntry();

            JarEntry classEntry = new JarEntry("WEB-INF/classes/");
            jarOut.putNextEntry(classEntry);
            jarOut.closeEntry();
        }

        // Create PackagedTarArchive from the WAR
        PackagedTarArchive tarArchive = new PackagedTarArchive(warFile);
        assertNotNull(tarArchive);

        // Write the tar archive to a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tarArchive.writeTo(outputStream);

        // Verify that output was generated
        byte[] tarData = outputStream.toByteArray();
        assertTrue(tarData.length > 0, "Tar archive should have data");
    }

    @Test
    public void testEmptyJarFile() throws IOException {
        // Create a minimal JAR file with just a manifest
        File jarFile = new File(tempDir, "empty.jar");
        try (JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(jarFile))) {
            // Add minimal manifest entry
            JarEntry manifestEntry = new JarEntry("META-INF/");
            jarOut.putNextEntry(manifestEntry);
            jarOut.closeEntry();
        }

        // Create PackagedTarArchive from the minimal JAR
        PackagedTarArchive tarArchive = new PackagedTarArchive(jarFile);
        assertNotNull(tarArchive);

        // Write the tar archive to a byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        tarArchive.writeTo(outputStream);

        // Even a minimal JAR should produce some tar output
        byte[] tarData = outputStream.toByteArray();
        assertTrue(tarData.length > 0, "Even minimal tar archive should have header data");
    }
}
