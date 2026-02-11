package com.tinusj.maven.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassPathTest {

    @TempDir
    File tempFolder;

    @Test
    public void testEmptyClassPath() {
        ClassPath cp = ClassPath.empty();
        assertTrue(cp.isEmpty());
        assertEquals("", cp.toString());
    }

    @Test
    public void testOfSingleEntry() {
        ClassPath cp = ClassPath.of("/some/path");
        assertFalse(cp.isEmpty());
        assertEquals("/some/path", cp.toString());
    }

    @Test
    public void testOfMultipleEntries() {
        ClassPath cp = ClassPath.of("/path/a", "/path/b");
        assertFalse(cp.isEmpty());
        String sep = File.pathSeparator;
        assertEquals("/path/a" + sep + "/path/b", cp.toString());
    }

    @Test
    public void testOfList() {
        ClassPath cp = ClassPath.of(Arrays.asList("/path/a", "/path/b", "/path/c"));
        String sep = File.pathSeparator;
        assertEquals("/path/a" + sep + "/path/b" + sep + "/path/c", cp.toString());
    }

    @Test
    public void testOfNullList() {
        ClassPath cp = ClassPath.of((List<String>) null);
        assertTrue(cp.isEmpty());
    }

    @Test
    public void testOfEmptyList() {
        ClassPath cp = ClassPath.of(Collections.emptyList());
        assertTrue(cp.isEmpty());
    }

    @Test
    public void testOfFiltersNullAndEmptyEntries() {
        ClassPath cp = ClassPath.of(Arrays.asList("/valid", null, "", "/also-valid"));
        String sep = File.pathSeparator;
        assertEquals("/valid" + sep + "/also-valid", cp.toString());
    }

    @Test
    public void testOfFiles() {
        File fileA = new File(tempFolder, "a.jar");
        File fileB = new File(tempFolder, "b.jar");
        ClassPath cp = ClassPath.ofFiles(Arrays.asList(fileA, fileB));
        assertFalse(cp.isEmpty());
        String sep = File.pathSeparator;
        assertEquals(fileA.getAbsolutePath() + sep + fileB.getAbsolutePath(), cp.toString());
    }

    @Test
    public void testOfFilesNull() {
        ClassPath cp = ClassPath.ofFiles(null);
        assertTrue(cp.isEmpty());
    }

    @Test
    public void testOfFilesEmpty() {
        ClassPath cp = ClassPath.ofFiles(Collections.emptyList());
        assertTrue(cp.isEmpty());
    }

    @Test
    public void testArgsNonEmpty() {
        ClassPath cp = ClassPath.of("/some/path");
        List<String> args = cp.args("-classpath");
        assertEquals(2, args.size());
        assertEquals("-classpath", args.get(0));
        assertEquals("/some/path", args.get(1));
    }

    @Test
    public void testArgsEmpty() {
        ClassPath cp = ClassPath.empty();
        List<String> args = cp.args("-classpath");
        assertTrue(args.isEmpty());
    }

    @Test
    public void testArgsWithDifferentFlags() {
        ClassPath cp = ClassPath.of("/some/path");

        List<String> cpArgs = cp.args("-cp");
        assertEquals("-cp", cpArgs.get(0));

        List<String> spArgs = cp.args("-sourcepath");
        assertEquals("-sourcepath", spArgs.get(0));
    }

    @Test
    public void testAppendBothNonEmpty() {
        ClassPath a = ClassPath.of("/path/a");
        ClassPath b = ClassPath.of("/path/b");
        ClassPath combined = a.append(b);
        String sep = File.pathSeparator;
        assertEquals("/path/a" + sep + "/path/b", combined.toString());
    }

    @Test
    public void testAppendToEmpty() {
        ClassPath a = ClassPath.empty();
        ClassPath b = ClassPath.of("/path/b");
        ClassPath combined = a.append(b);
        assertEquals("/path/b", combined.toString());
    }

    @Test
    public void testAppendEmpty() {
        ClassPath a = ClassPath.of("/path/a");
        ClassPath combined = a.append(ClassPath.empty());
        assertEquals("/path/a", combined.toString());
    }

    @Test
    public void testAppendNull() {
        ClassPath a = ClassPath.of("/path/a");
        ClassPath combined = a.append(null);
        assertEquals("/path/a", combined.toString());
    }

    @Test
    public void testAppendBothEmpty() {
        ClassPath combined = ClassPath.empty().append(ClassPath.empty());
        assertTrue(combined.isEmpty());
    }

    @Test
    public void testToString() {
        ClassPath cp = ClassPath.of(Arrays.asList("/a", "/b"));
        String sep = File.pathSeparator;
        assertEquals("/a" + sep + "/b", cp.toString());
    }
}
