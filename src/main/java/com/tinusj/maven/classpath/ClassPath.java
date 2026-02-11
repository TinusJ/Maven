package com.tinusj.maven.classpath;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Encapsulates a class path and provides utility methods for building
 * and converting class paths. Ensures consistent class path handling
 * throughout the plugin.
 * <p>
 * On Windows an argument file is used whenever possible since the maximum
 * command line length is limited.
 * </p>
 *
 * @see <a href="https://github.com/spring-projects/spring-boot/blob/main/build-plugin/spring-boot-maven-plugin/src/main/java/org/springframework/boot/maven/ClassPath.java">
 *     Spring Boot ClassPath</a>
 */
final class ClassPath {

    private static final Collector<CharSequence, ?, String> JOIN_BY_PATH_SEPARATOR = Collectors
            .joining(File.pathSeparator);

    private static final ClassPath EMPTY = new ClassPath(false, "");

    private final boolean preferArgFile;

    private final String path;

    private ClassPath(boolean preferArgFile, String path) {
        this.preferArgFile = preferArgFile;
        this.path = path;
    }

    /**
     * Return the args to append to a command line call using the given flag
     * (e.g. {@code -cp}, {@code -classpath}, {@code -sourcepath}).
     * @param flag the command line flag to use
     * @param allowArgFile if an arg file can be used
     * @return the command line arguments, or an empty list if this class path is empty
     */
    List<String> args(String flag, boolean allowArgFile) {
        return !isEmpty() ? List.of(flag, pathArg(allowArgFile)) : List.of();
    }

    /**
     * Return the args to append to a command line call using the given flag.
     * Arg files are not used.
     * @param flag the command line flag to use
     * @return the command line arguments, or an empty list if this class path is empty
     */
    List<String> args(String flag) {
        return args(flag, false);
    }

    private String pathArg(boolean allowArgFile) {
        if (this.preferArgFile && allowArgFile) {
            try {
                return "@" + createArgFile();
            }
            catch (IOException ex) {
                return this.path;
            }
        }
        return this.path;
    }

    /**
     * Returns {@code true} if this class path has no entries.
     * @return true if empty
     */
    boolean isEmpty() {
        return this.path.isEmpty();
    }

    /**
     * Appends another {@link ClassPath} to this one.
     * @param other the class path to append
     * @return a new {@link ClassPath} containing both paths
     */
    ClassPath append(ClassPath other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        return new ClassPath(this.preferArgFile || other.preferArgFile,
                this.path + File.pathSeparator + other.path);
    }

    @Override
    public String toString() {
        return this.path;
    }

    private Path createArgFile() throws IOException {
        Path argFile = Files.createTempFile("classpath-plugin-", ".argfile");
        argFile.toFile().deleteOnExit();
        Files.writeString(argFile, "\"" + this.path.replace("\\", "\\\\") + "\"", charset());
        return argFile;
    }

    private Charset charset() {
        try {
            String nativeEncoding = System.getProperty("native.encoding");
            return (nativeEncoding != null) ? Charset.forName(nativeEncoding) : Charset.defaultCharset();
        }
        catch (UnsupportedCharsetException ex) {
            return Charset.defaultCharset();
        }
    }

    /**
     * Factory method to create a {@link ClassPath} from the given path entries.
     * @param entries the class path entries (absolute paths)
     * @return a new {@link ClassPath} instance
     */
    static ClassPath of(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EMPTY;
        }
        List<String> filtered = entries.stream()
                .filter(e -> e != null && !e.isEmpty())
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return EMPTY;
        }
        boolean preferArgFile = filtered.size() > 1 && isWindows();
        return new ClassPath(preferArgFile, filtered.stream().collect(JOIN_BY_PATH_SEPARATOR));
    }

    /**
     * Factory method to create a {@link ClassPath} from the given path entries.
     * @param entries the class path entries (absolute paths)
     * @return a new {@link ClassPath} instance
     */
    static ClassPath of(String... entries) {
        return of(Arrays.asList(entries));
    }

    /**
     * Factory method to create a {@link ClassPath} from the given files.
     * Each file's absolute path is used as a class path entry.
     * @param files the files to use as class path entries
     * @return a new {@link ClassPath} instance
     */
    static ClassPath ofFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return EMPTY;
        }
        List<String> paths = new ArrayList<>(files.size());
        for (File file : files) {
            if (file != null) {
                paths.add(file.getAbsolutePath());
            }
        }
        return of(paths);
    }

    /**
     * Returns an empty {@link ClassPath}.
     * @return an empty class path
     */
    static ClassPath empty() {
        return EMPTY;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

}
