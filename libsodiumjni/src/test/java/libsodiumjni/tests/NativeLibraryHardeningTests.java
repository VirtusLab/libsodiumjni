package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Assume;
import org.junit.Test;

/**
 * Checks that the libsodiumjni native code is compiled with stack protection
 * (-fstack-protector-strong), which makes it import the stack canary failure handler. Many
 * toolchains enable it by default, but the build shouldn't rely on that.
 *
 * <p>This checks the object files of the libsodiumjni code (passed by the build in the
 * libsodiumjni.test.objectFiles property), rather than the native library: libsodium is linked
 * statically in the latter on Linux and macOS, and its own objects import that handler, whatever
 * the flags used for the libsodiumjni code.
 */
public class NativeLibraryHardeningTests {

    private static boolean nmAvailable() {
        try {
            Process proc = new ProcessBuilder("nm", "--version").redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            proc.waitFor();
            return true;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    public void nativeCodeHasStackProtection() throws Exception {
        String objectFiles = System.getProperty("libsodiumjni.test.objectFiles", "");
        Assume.assumeFalse("no object files passed by the build (Windows, or not run by Mill)", objectFiles.isEmpty());
        Assume.assumeTrue("needs nm", nmAvailable());

        for (String objectFile : objectFiles.split(File.pathSeparator)) {
            // undefined (imported) symbols, e.g. "___stack_chk_fail" on macOS, "__stack_chk_fail" on Linux
            Process proc = new ProcessBuilder("nm", "-u", objectFile).redirectErrorStream(true).start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("nm failed:\n" + output, 0, proc.waitFor());
            assertFalse("No undefined symbols found in " + objectFile + ", is nm output parsed correctly?", output.isBlank());
            assertTrue(
                    objectFile + " doesn't import __stack_chk_fail, is it compiled with stack protection?\n" + output,
                    output.contains("__stack_chk_fail"));
        }
    }
}
