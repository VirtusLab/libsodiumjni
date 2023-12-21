package libsodiumjni.internal;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class LoadLibrary {

    private static boolean initialized = false;

    public static void initialize(String libraryPath) {
        if (!initialized) {
            synchronized (LoadLibrary.class) {
                System.load(libraryPath);
                initialized = true;
            }
        }
    }

    public static String resourcePath() {
        Boolean isArmArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT).contains("aarch64");

        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) return "META-INF/native/linux64/libsodiumjni.so";
        else if (osName.contains("mac") && isArmArch) return "META-INF/native/arm-darwin/libsodiumjni.dylib";
        else if (osName.contains("mac")) return "META-INF/native/darwin/libsodiumjni.dylib";
        else if (osName.contains("windows")) return "META-INF/native/windows64/libsodiumjni.dll";
        else
          throw new RuntimeException("Unrecognized or unsupported OS: " + osName);
    }

    public static String libSuffix() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) return ".so";
        else if (osName.contains("mac")) return ".dylib";
        else if (osName.contains("windows")) return ".dll";
        else
          throw new RuntimeException("Unrecognized or unsupported OS: " + osName);
    }


    public static void initializeFromResources() throws IOException {
        initializeFromResources(resourcePath());
    }

    public static void initializeFromResources(String resourcePath) throws IOException {
        if (!initialized) {
            synchronized (LoadLibrary.class) {
                URL resUrl = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
                if (resUrl == null)
                    throw new RuntimeException("Resource " + resourcePath + " not found");
                byte[] content = resUrl.openStream().readAllBytes();
                Path lib = Files.createTempFile("libsodium", libSuffix());
                Files.write(lib, content);
                System.load(lib.toString());
                initialized = true;
            }
        }
    }

    public static void assumeInitialized() {
        initialized = true;
    }

}
