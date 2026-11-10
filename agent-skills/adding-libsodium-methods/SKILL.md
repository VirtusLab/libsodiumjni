---
name: libsodiumjni-adding-libsodium-methods
description: Expose a new libsodium function through libsodiumjni's JNI bindings. Use when adding or changing a native method in SodiumApi, its C implementation, or the public Sodium wrapper.
---

# Adding a libsodium method (libsodiumjni)

1. **Native method**: add a `public static native` method in
   `libsodiumjni/src/main/java/libsodiumjni/internal/SodiumApi.java`. Avoid parameters that duplicate array lengths
   (the native side can get them with `GetArrayLength`).

2. **Header**: run `./mill -i __.compile`. `javac -h` updates
   `libsodiumjni/src/main/c/libsodiumjni_internal_SodiumApi.h`. Commit it: the CI fails if the build modifies tracked
   files.

3. **C implementation** in `libsodiumjni_internal_SodiumApi.c`, following the existing functions:
   - reject null arrays first (`is_null`, which throws a `NullPointerException`, then return
     `LIBSODIUMJNI_EXCEPTION_THROWN`)
   - check every length against the others and against libsodium's sizes (the `jsize` constants like
     `PUBLIC_KEY_BYTES`) before any memory access, and return `LIBSODIUMJNI_INVALID_ARGUMENTS` if they don't match
   - copy array contents with `GetByteArrayRegion` into buffers allocated here (stack for keys, `checked_malloc` for
     variable sizes), and back with `SetByteArrayRegion`, only for what libsodium wrote
   - wipe buffers that held secrets (plaintexts, secret keys) with `wipe` / `free_wiped` on every return path
   - no compiler warnings: they're errors on Linux / macOS (`UNUSED(x)` for unused JNI parameters)

4. **Public wrapper** in `libsodiumjni/src/main/java/libsodiumjni/Sodium.java`: validate arguments
   (`NullPointerException` / `IllegalArgumentException` with a message naming the argument), and throw
   `SodiumException` when libsodium fails. Don't change the signatures of existing public methods: Scala CLI uses
   them (see AGENTS.md).

5. **Tests** (in `libsodiumjni/src/test/java/libsodiumjni/tests/`), written first: behavior (`SodiumTests`), invalid
   sizes (`SodiumInputValidationTests`), null arrays (`SodiumNullArgumentsTests`), at both the `Sodium` and `SodiumApi`
   levels. If the function handles secrets, extend `NativeMemoryWipingTests` / `FreeCheckerChild`.

6. Run `./mill -i __.test`, and on Linux too if possible (see the `linux-docker-testing` skill). Running the tests
   with `JAVA_TOOL_OPTIONS=-Xcheck:jni` catches JNI misuse.
