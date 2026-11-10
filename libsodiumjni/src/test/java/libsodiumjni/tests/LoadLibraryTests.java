package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Test;

public class LoadLibraryTests {

    @Test
    public void linuxX86_64ResourcePath() {
        assertEquals("META-INF/native/linux64/libsodiumjni.so", LoadLibrary.resourcePath("Linux", "amd64"));
        assertEquals("META-INF/native/linux64/libsodiumjni.so", LoadLibrary.resourcePath("Linux", "x86_64"));
    }

    @Test
    public void linuxAarch64ResourcePath() {
        assertEquals("META-INF/native/linux-aarch64/libsodiumjni.so", LoadLibrary.resourcePath("Linux", "aarch64"));
    }

    @Test
    public void linuxUnsupportedArchitectureFails() {
        // Loading the x86_64 library there can only fail, with a less helpful error
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> LoadLibrary.resourcePath("Linux", "ppc64le"));
        assertEquals("Unsupported architecture on Linux: ppc64le", ex.getMessage());
    }

    @Test
    public void macOsResourcePaths() {
        assertEquals("META-INF/native/arm-darwin/libsodiumjni.dylib", LoadLibrary.resourcePath("Mac OS X", "aarch64"));
        assertEquals("META-INF/native/darwin/libsodiumjni.dylib", LoadLibrary.resourcePath("Mac OS X", "x86_64"));
    }

    @Test
    public void macOsUnsupportedArchitectureFails() {
        // Loading the x86_64 library there can only fail, with a less helpful error
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> LoadLibrary.resourcePath("Mac OS X", "ppc"));
        assertEquals("Unsupported architecture on macOS: ppc", ex.getMessage());
    }

    @Test
    public void windowsResourcePath() {
        assertEquals("META-INF/native/windows64/libsodiumjni.dll", LoadLibrary.resourcePath("Windows 11", "amd64"));
    }

    @Test
    public void windowsAarch64ResourcePath() {
        // native arm64 JVM on Windows on ARM
        assertEquals("META-INF/native/windows-aarch64/libsodiumjni.dll", LoadLibrary.resourcePath("Windows 11", "aarch64"));
    }

    @Test
    public void windowsUnsupportedArchitecturesFail() {
        // 32-bit JVM
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> LoadLibrary.resourcePath("Windows 10", "x86"));
        assertEquals("Unsupported architecture on Windows: x86", ex.getMessage());
    }

    @Test
    public void unsupportedOsFails() {
        assertThrows(UnsupportedOperationException.class, () -> LoadLibrary.resourcePath("FreeBSD", "amd64"));
    }
}
