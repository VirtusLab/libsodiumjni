package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Assume;
import org.junit.Test;

/**
 * Checks LoadLibrary.initializeFromResources() in fresh JVMs (see LoadLibraryChild), each with its
 * own temporary directory, to check what is left there.
 */
public class LoadLibraryInitializationTests {

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    /**
     * Files the extracted library may leave behind in the temporary directory: none where a loaded
     * library can be deleted, a single cached one on Windows, where it can't
     */
    private static int maxLeftOverFiles() {
        return isWindows() ? 1 : 0;
    }

    private static void runChild(String scenario, Path tmpDir, String... extraArgs) throws Exception {
        List<String> args = new ArrayList<>();
        args.add(scenario);
        args.addAll(List.of(extraArgs));
        ChildJvm.Result result = ChildJvm.run(
                List.of("-Djava.io.tmpdir=" + tmpDir),
                LoadLibraryChild.class,
                args,
                Map.of(),
                null);
        assertTrue(
                "Scenario '" + scenario + "' failed (exit code " + result.exitCode + "):\n" + result.output,
                result.exitCode == 0 && result.output.contains("OK"));
    }

    private static List<String> files(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    private static void withTmpDir(TmpDirTest test) throws Exception {
        Path tmpDir = Files.createTempDirectory("libsodiumjni-load-tests");
        try {
            test.run(tmpDir);
        } finally {
            try (Stream<Path> paths = Files.walk(tmpDir)) {
                for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }

    private interface TmpDirTest {
        void run(Path tmpDir) throws Exception;
    }

    @Test
    public void loadsWithNullContextClassLoader() throws Exception {
        withTmpDir(tmpDir -> runChild("null-context-class-loader", tmpDir));
    }

    @Test
    public void missingResourceThrowsIllegalStateException() throws Exception {
        withTmpDir(tmpDir -> runChild("missing-resource", tmpDir));
    }

    @Test
    public void extractedLibrariesDontPileUp() throws Exception {
        withTmpDir(tmpDir -> {
            runChild("load", tmpDir);
            runChild("load", tmpDir);
            List<String> files = files(tmpDir);
            assertTrue(
                    "Expected at most " + maxLeftOverFiles() + " file(s) left in the temporary directory after two runs, got " + files,
                    files.size() <= maxLeftOverFiles());
        });
    }

    @Test
    public void concurrentLoadsExtractTheLibraryOnce() throws Exception {
        withTmpDir(tmpDir -> {
            runChild("concurrent", tmpDir);
            List<String> files = files(tmpDir);
            assertTrue(
                    "Expected at most " + maxLeftOverFiles() + " file(s) left in the temporary directory, got " + files,
                    files.size() <= maxLeftOverFiles());
        });
    }

    @Test
    public void windowsCacheIsReused() throws Exception {
        org.junit.Assume.assumeTrue("Windows only", isWindows());
        withTmpDir(tmpDir -> {
            runChild("load", tmpDir);
            List<String> first = files(tmpDir);
            runChild("load", tmpDir);
            assertEquals(first, files(tmpDir));
            assertEquals(1, first.size());
        });
    }

    /**
     * Extracts the native library bundled for this platform to dir (created if needed), as Scala CLI
     * gets it from Maven Central, for LoadLibrary.initialize(path)
     */
    private static Path extractLibrary(Path dir) throws IOException {
        String resourcePath = LoadLibrary.resourcePath();
        URL url = LoadLibraryInitializationTests.class.getClassLoader().getResource(resourcePath);
        assertTrue("Resource " + resourcePath + " not found", url != null);
        Files.createDirectories(dir);
        Path lib = dir.resolve("libsodiumjni" + LoadLibrary.libSuffix());
        try (InputStream is = url.openStream()) {
            Files.copy(is, lib);
        }
        return lib;
    }

    /**
     * Loads the library from a path with LoadLibrary.initialize, then checks that later calls of
     * initialize / initializeFromResources are no-ops (in particular, that nothing gets extracted to
     * the temporary directory)
     */
    private static void checkInitializeFromPath(String libDirName) throws Exception {
        withTmpDir(dir -> {
            Path lib = extractLibrary(dir.resolve(libDirName));
            Path childTmpDir = Files.createDirectory(dir.resolve("tmp"));
            runChild("initialize-path", childTmpDir, lib.toAbsolutePath().toString());
            assertEquals(List.of(), files(childTmpDir));
        });
    }

    @Test
    public void initializeLoadsFromPath() throws Exception {
        checkInitializeFromPath("lib");
    }

    @Test
    public void initializeLoadsFromPathWithSpaces() throws Exception {
        // e.g. a coursier cache under "C:\Users\John Doe"
        checkInitializeFromPath("lib dir with spaces");
    }

    @Test
    public void initializeLoadsFromPathWithNonAsciiCharacters() throws Exception {
        // e.g. a coursier cache under the home directory of a user with such a name
        String dirName = "lib-\u00e9\u00fc";
        String jnuEncoding = System.getProperty("sun.jnu.encoding");
        Assume.assumeTrue(
                "file names with non-ASCII characters aren't supported here (sun.jnu.encoding: " + jnuEncoding + ")",
                jnuEncoding != null && Charset.isSupported(jnuEncoding)
                        && Charset.forName(jnuEncoding).newEncoder().canEncode(dirName));
        checkInitializeFromPath(dirName);
    }

    @Test
    public void initializeCanBeRetriedAfterAFailure() throws Exception {
        withTmpDir(dir -> {
            Path lib = extractLibrary(dir.resolve("lib"));
            Path childTmpDir = Files.createDirectory(dir.resolve("tmp"));
            runChild("initialize-path-retry", childTmpDir, lib.toAbsolutePath().toString());
        });
    }

    @Test
    public void assumeInitializedMakesLaterLoadsNoOps() throws Exception {
        withTmpDir(tmpDir -> {
            runChild("assume-initialized", tmpDir);
            assertEquals(List.of(), files(tmpDir));
        });
    }
}
