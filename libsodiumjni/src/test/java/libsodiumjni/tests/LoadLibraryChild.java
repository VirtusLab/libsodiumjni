package libsodiumjni.tests;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;
import libsodiumjni.internal.SodiumApi;

/**
 * Run in a fresh JVM by LoadLibraryInitializationTests, so that the native library isn't loaded
 * yet.
 *
 * <p>Arguments: scenario, "load", "null-context-class-loader", "concurrent", "missing-resource",
 * "assume-initialized", "initialize-path" or "initialize-path-retry", followed, for the
 * "initialize-path*" ones, by the path of the native library to load. Prints "OK" and exits with 0
 * on success.
 */
public class LoadLibraryChild {

    private static final int THREADS = 16;

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "missing-resource":
                try {
                    LoadLibrary.initializeFromResources("META-INF/native/missing/libsodiumjni.so");
                    throw new AssertionError("Expected an exception");
                } catch (IllegalStateException e) {
                    if (!e.getMessage().equals("Resource META-INF/native/missing/libsodiumjni.so not found"))
                        throw new AssertionError("Unexpected message: " + e.getMessage());
                }
                System.out.println("OK");
                return;

            case "load":
                LoadLibrary.initializeFromResources();
                break;

            case "null-context-class-loader":
                // e.g. threads created from native code, some thread pools, …
                Thread.currentThread().setContextClassLoader(null);
                LoadLibrary.initializeFromResources();
                break;

            case "concurrent":
                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
                List<Thread> threads = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    Thread thread = new Thread(() -> {
                        try {
                            barrier.await();
                            LoadLibrary.initializeFromResources();
                        } catch (Throwable t) {
                            failures.add(t);
                        }
                    });
                    thread.start();
                    threads.add(thread);
                }
                for (Thread thread : threads)
                    thread.join();
                if (!failures.isEmpty()) {
                    System.out.println(failures.size() + " of " + THREADS + " concurrent calls failed:");
                    failures.get(0).printStackTrace(System.out);
                    System.exit(1);
                }
                break;

            case "assume-initialized":
                // e.g. a GraalVM native image, where the library is linked statically
                LoadLibrary.assumeInitialized();
                // no-ops from now on, whatever they're passed
                LoadLibrary.initialize(missingLibrary().toString());
                LoadLibrary.initializeFromResources("META-INF/native/missing/libsodiumjni.so");
                LoadLibrary.initializeFromResources();
                // nothing was actually loaded
                try {
                    SodiumApi.sodium_init();
                    throw new AssertionError("Expected the native library not to be loaded");
                } catch (UnsatisfiedLinkError e) {
                    // expected
                }
                System.out.println("OK");
                return;

            case "initialize-path":
                // as Scala CLI does, with a library fetched from Maven Central
                LoadLibrary.initialize(args[1]);
                // no-ops from now on, whatever they're passed (the caller checks that nothing was
                // extracted to the temporary directory)
                LoadLibrary.initialize(missingLibrary().toString());
                LoadLibrary.initializeFromResources();
                break;

            case "initialize-path-retry":
                // a failed load doesn't count as loaded
                try {
                    LoadLibrary.initialize(missingLibrary().toString());
                    throw new AssertionError("Expected loading a missing library to fail");
                } catch (UnsatisfiedLinkError e) {
                    // expected
                }
                LoadLibrary.initialize(args[1]);
                break;

            default:
                throw new IllegalArgumentException("Unknown scenario: " + args[0]);
        }

        // the library must be usable afterwards
        Sodium.init();
        Sodium.seal(new byte[] {1, 2, 3}, Sodium.keyPair().getPubKey());
        System.out.println("OK");
    }

    /** Absolute path of a native library that doesn't exist */
    private static Path missingLibrary() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "does-not-exist", "libsodiumjni" + LoadLibrary.libSuffix())
                .toAbsolutePath();
    }
}
