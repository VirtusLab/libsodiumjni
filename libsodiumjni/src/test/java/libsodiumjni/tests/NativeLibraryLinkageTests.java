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

    private static Path extractResource(String resourcePath, String suffix) throws IOException {
        URL url = NativeLibraryLinkageTests.class.getClassLoader().getResource(resourcePath);
        assertNotNull("Resource " + resourcePath + " not found", url);
        Path dest = Files.createTempFile("libsodiumjni-linkage", suffix);
        try (InputStream is = url.openStream()) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return dest;
    }

    /** Paths of the dylibs loaded by {@code lib}, as listed by {@code otool -l} (excludes its own ID). */
    private static List<String> dylibDependencies(Path lib) throws IOException, InterruptedException {
        Process proc = new ProcessBuilder("otool", "-l", lib.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("otool -l failed:\n" + output, 0, proc.waitFor());

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
