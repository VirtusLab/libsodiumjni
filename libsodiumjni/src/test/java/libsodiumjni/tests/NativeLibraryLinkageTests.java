package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Assume;
import org.junit.Test;

/**
 * Checks how the bundled native library is linked.
 *
 * <p>On macOS and Linux, libsodium is linked statically in it, so that it only depends on system
 * libraries, and doesn't require a specific libsodium version to be installed.
 */
public class NativeLibraryLinkageTests {

    private static final Set<String> LOAD_DYLIB_COMMANDS = Set.of(
            "LC_LOAD_DYLIB",
            "LC_LOAD_WEAK_DYLIB",
            "LC_REEXPORT_DYLIB",
            "LC_LAZY_LOAD_DYLIB",
            "LC_LOAD_UPWARD_DYLIB");

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
    }

    /**
     * Newest glibc version the Linux library may require, i.e. oldest glibc version it must load
     * with.
     *
     * <p>As libsodium is linked statically, its glibc requirements (e.g. getrandom, glibc 2.25) are
     * the library's. And as it's built on recent glibc versions (Ubuntu 24.04 runners on the CI),
     * fstat ends up requiring glibc 2.33 (it became a versioned function then). This check makes
     * sure that this doesn't increase silently (when the CI runners get updated, for example).
     */
    private static final int[] MAX_GLIBC_VERSION = {2, 33};

    /** glibc libraries the Linux library may depend on */
    private static boolean isGlibcLibrary(String soname) {
        return soname.equals("libc.so.6")
                || soname.equals("libm.so.6")
                || soname.equals("libdl.so.2")
                || soname.equals("libpthread.so.0")
                || soname.equals("librt.so.1")
                || soname.startsWith("ld-linux");
    }

    private static boolean toolAvailable(String tool) {
        try {
            Process proc = new ProcessBuilder(tool, "--version").redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            return proc.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(String.join(" ", command) + " failed:\n" + output, 0, proc.waitFor());
        return output;
    }

    @Test
    public void linuxLibraryOnlyDependsOnGlibc() throws Exception {
        Assume.assumeTrue("Linux-only check", isLinux());
        Assume.assumeTrue("needs readelf", toolAvailable("readelf"));

        Path lib = extractResource(LoadLibrary.resourcePath(), ".so");
        try {
            // e.g. " 0x0000000000000001 (NEEDED)             Shared library: [libc.so.6]"
            List<String> needed = new ArrayList<>();
            for (String line : run("readelf", "-d", lib.toString()).split("\n"))
                if (line.contains("(NEEDED)"))
                    needed.add(line.substring(line.indexOf('[') + 1, line.lastIndexOf(']')));
            assertFalse("No NEEDED entries found, is readelf output parsed correctly?", needed.isEmpty());
            List<String> nonGlibc = needed.stream().filter(l -> !isGlibcLibrary(l)).collect(Collectors.toList());
            assertTrue(
                    "The Linux library must only depend on glibc (libsodium should be linked statically), "
                            + "but it depends on: " + nonGlibc,
                    nonGlibc.isEmpty());
        } finally {
            Files.deleteIfExists(lib);
        }
    }

    @Test
    public void linuxLibraryOnlyExportsJniFunctions() throws Exception {
        Assume.assumeTrue("Linux-only check", isLinux());
        Assume.assumeTrue("needs nm", toolAvailable("nm"));

        Path lib = extractResource(LoadLibrary.resourcePath(), ".so");
        try {
            // e.g. "0000000000000a10 T Java_libsodiumjni_internal_SodiumApi_sodium_1init"
            List<String> exported = new ArrayList<>();
            for (String line : run("nm", "-D", "--defined-only", lib.toString()).split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 3) exported.add(parts[2]);
            }
            assertFalse("No exported symbols found, is nm output parsed correctly?", exported.isEmpty());
            // The symbols of the statically linked libsodium mustn't be exported: they could clash
            // with those of another libsodium loaded in the same process
            List<String> nonJni = exported.stream().filter(s -> !s.startsWith("Java_")).collect(Collectors.toList());
            assertTrue("The Linux library must only export JNI functions, but it also exports: " + nonJni, nonJni.isEmpty());
        } finally {
            Files.deleteIfExists(lib);
        }
    }

    @Test
    public void linuxLibraryLoadsWithOldGlibcVersions() throws Exception {
        Assume.assumeTrue("Linux-only check", isLinux());
        Assume.assumeTrue("needs objdump", toolAvailable("objdump"));

        Path lib = extractResource(LoadLibrary.resourcePath(), ".so");
        try {
            // versions of the glibc symbols it imports, e.g. "GLIBC_2.17"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("GLIBC_(\\d+)\\.(\\d+)")
                    .matcher(run("objdump", "-T", lib.toString()));
            int[] max = {0, 0};
            while (m.find()) {
                int[] v = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
                if (v[0] > max[0] || (v[0] == max[0] && v[1] > max[1])) max = v;
            }
            assertTrue("No glibc symbol versions found, is objdump output parsed correctly?", max[0] > 0);
            boolean ok = max[0] < MAX_GLIBC_VERSION[0]
                    || (max[0] == MAX_GLIBC_VERSION[0] && max[1] <= MAX_GLIBC_VERSION[1]);
            assertTrue(
                    "The Linux library requires glibc " + max[0] + "." + max[1] + ", but it must load with glibc "
                            + MAX_GLIBC_VERSION[0] + "." + MAX_GLIBC_VERSION[1],
                    ok);
        } finally {
            Files.deleteIfExists(lib);
        }
    }

    private static boolean isSystemLibrary(String path) {
        return path.startsWith("/usr/lib/") || path.startsWith("/System/Library/");
    }

    @Test
    public void macOsDylibOnlyDependsOnSystemLibraries() throws Exception {
        Assume.assumeTrue("macOS-only check", isMac());

        Path dylib = extractResource(LoadLibrary.resourcePath(), ".dylib");
        try {
            List<String> dependencies = dylibDependencies(dylib);
            assertFalse("No dynamic dependencies found in " + dylib + ", is otool output parsed correctly?",
                    dependencies.isEmpty());

            List<String> nonSystem = dependencies.stream()
                    .filter(dep -> !isSystemLibrary(dep))
                    .collect(Collectors.toList());
            assertTrue(
                    "libsodiumjni.dylib must only depend on system libraries (libsodium should be linked "
                            + "statically), but it depends on: " + nonSystem,
                    nonSystem.isEmpty());
        } finally {
            Files.deleteIfExists(dylib);
        }
    }

    /**
     * Oldest macOS version the dylib must load on. 11.0 is the first macOS release supporting Apple
     * Silicon. Must match the deployment target in build.mill.
     */
    private static final int[] MACOS_DEPLOYMENT_TARGET = {11, 0};

    @Test
    public void macOsDylibTargetsOldMacOsVersions() throws Exception {
        Assume.assumeTrue("macOS-only check", isMac());

        Path dylib = extractResource(LoadLibrary.resourcePath(), ".dylib");
        try {
            String minOs = dylibMinOs(dylib);
            assertTrue(
                    "libsodiumjni.dylib requires macOS " + minOs + ", but it must load on macOS "
                            + MACOS_DEPLOYMENT_TARGET[0] + "." + MACOS_DEPLOYMENT_TARGET[1]
                            + " (set the deployment target when compiling it and libsodium)",
                    compareVersions(minOs, MACOS_DEPLOYMENT_TARGET) <= 0);
        } finally {
            Files.deleteIfExists(dylib);
        }
    }

    private static int compareVersions(String version, int[] other) {
        String[] parts = version.split("\\.");
        for (int i = 0; i < Math.max(parts.length, other.length); i++) {
            int a = i < parts.length ? Integer.parseInt(parts[i]) : 0;
            int b = i < other.length ? other[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    private static Path extractResource(String resourcePath, String suffix) throws IOException {
        URL url = NativeLibraryLinkageTests.class.getClassLoader().getResource(resourcePath);
        assertNotNull("Resource " + resourcePath + " not found", url);
        Path dest = Files.createTempFile("libsodiumjni-linkage", suffix);
        try (InputStream is = url.openStream()) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return dest;
    }

    private static String otoolLoadCommands(Path lib) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder("otool", "-l", lib.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("otool -l failed:\n" + output, 0, proc.waitFor());
        return output;
    }

    /**
     * Minimum macOS version of {@code lib}, from its LC_BUILD_VERSION ("minos") or, for older
     * binaries, LC_VERSION_MIN_MACOSX ("version") load command.
     */
    private static String dylibMinOs(Path lib) throws IOException, InterruptedException {
        String output = otoolLoadCommands(lib);
        String cmd = null;
        for (String rawLine : output.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("cmd ")) {
                cmd = line.substring("cmd ".length()).trim();
            } else if ("LC_BUILD_VERSION".equals(cmd) && line.startsWith("minos ")) {
                return line.substring("minos ".length()).trim();
            } else if ("LC_VERSION_MIN_MACOSX".equals(cmd) && line.startsWith("version ")) {
                return line.substring("version ".length()).trim();
            }
        }
        throw new AssertionError("No minimum macOS version found in otool -l output for " + lib + ":\n" + output);
    }

    /** Paths of the dylibs loaded by {@code lib}, as listed by {@code otool -l} (excludes its own ID). */
    private static List<String> dylibDependencies(Path lib) throws IOException, InterruptedException {
        String output = otoolLoadCommands(lib);

        List<String> dependencies = new ArrayList<>();
        boolean inLoadDylibCommand = false;
        for (String rawLine : output.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("cmd ")) {
                inLoadDylibCommand = LOAD_DYLIB_COMMANDS.contains(line.substring("cmd ".length()).trim());
            } else if (inLoadDylibCommand && line.startsWith("name ")) {
                String name = line.substring("name ".length());
                int offsetIdx = name.lastIndexOf(" (offset");
                dependencies.add(offsetIdx >= 0 ? name.substring(0, offsetIdx) : name);
                inLoadDylibCommand = false;
            }
        }
        return dependencies;
    }
}
