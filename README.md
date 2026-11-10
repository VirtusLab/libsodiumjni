# libsodiumjni

[![Build status](https://github.com/VirtusLab/libsodiumjni/workflows/CI/badge.svg)](https://github.com/VirtusLab/libsodiumjni/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/org.virtuslab.scala-cli/libsodiumjni.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.virtuslab.scala-cli/libsodiumjni)
[![Discord](https://img.shields.io/discord/632277896739946517.svg?label=&logo=discord&logoColor=ffffff&color=404244&labelColor=6A7EC2)](https://discord.gg/KzQdYkZZza)

Minimal JNI bindings for libsodium

This project provides JNI bindings for some [libsodium](https://github.com/jedisct1/libsodium) methods.

It's used by [Scala CLI](https://github.com/VirtusLab/scala-cli) to encrypt repository secrets about to be uploaded to GitHub via the GitHub API. For now, it only exposes the libsodium methods that Scala CLI needs: key pair generation, and sealed boxes (`crypto_box_seal` / `crypto_box_seal_open`).

The motivation for writing libsodiumjni, rather than using any of the existing JNA-based bindings, is mainly using JNI rather than JNA to interface with libsodium, as JNI is supported from GraalVM native images, while JNA is not. libsodiumjni has no dependencies.

## Supported platforms

| OS      | CPU     | Classifier             |
|---------|---------|------------------------|
| Linux   | x86_64  | `x86_64-pc-linux`      |
| Linux   | arm64   | `aarch64-pc-linux`     |
| macOS   | x86_64  | `x86_64-apple-darwin`  |
| macOS   | arm64   | `aarch64-apple-darwin` |
| Windows | x86_64  | `x86_64-pc-win32`      |
| Windows | arm64   | `aarch64-pc-win32`     |

Linux arm64 and Windows arm64 are supported since 1.0.0. On other platforms, loading the native library fails with an `UnsupportedOperationException`.

libsodiumjni requires Java 17 or later.

## Usage

libsodiumjni is published to [Maven Central](https://repo1.maven.org/maven2/org/virtuslab/scala-cli/libsodiumjni/), as `org.virtuslab.scala-cli:libsodiumjni`.

With Mill:
```scala
def mvnDeps = Seq(
  mvn"org.virtuslab.scala-cli:libsodiumjni:1.0.0"
)
```

With sbt:
```scala
libraryDependencies += "org.virtuslab.scala-cli" % "libsodiumjni" % "1.0.0"
```

With Scala CLI:
```scala
//> using dep org.virtuslab.scala-cli:libsodiumjni:1.0.0
```

Then load the native library, initialize libsodium, and use the methods of `libsodiumjni.Sodium`:
```java
import libsodiumjni.KeyPair;
import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;

LoadLibrary.initializeFromResources(); // loads the native library bundled in the JAR
Sodium.init();

KeyPair keyPair = Sodium.keyPair();
byte[] cipher = Sodium.seal(message, keyPair.getPubKey());
byte[] decrypted = Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey());
```

The native library is loaded at most once per JVM, and `Sodium.init` can be called several times, from several threads. Errors are reported with:
- `IllegalArgumentException` / `NullPointerException`, for invalid arguments (keys of the wrong length, cipher too short, null arrays, …)
- `libsodiumjni.SodiumException`, when libsodium fails, e.g. when a cipher can't be opened, because of a wrong key pair, or a corrupted / tampered cipher

### Native library dependencies

On macOS, libsodium is linked statically in the native library, so it has no dependencies besides the system libraries.

On Linux and Windows, the native library links dynamically against libsodium, which must be either installed, or loaded beforehand (with `System.load`):
- Linux: `libsodium.so.23` (libsodium 1.0.18, the one of Ubuntu 24.04, e.g. from the `libsodium23` package)
- Windows: `libsodium.dll` (libsodium 1.0.21, from its [official MSVC build](https://download.libsodium.org/libsodium/releases/))

### Native library files

The native libraries are also published as is, with the classifiers above, both as shared libraries (`.so` / `.dylib` / `.dll`) and as static ones (`.a` / `.lib`, for GraalVM native images for example). You can fetch them with commands such as
```text
$ cs fetch --intransitive "org.virtuslab.scala-cli:libsodiumjni:1.0.0,classifier=x86_64-pc-win32,ext=dll,type=dll" -A dll
```

To load a shared library fetched that way, rather than the one bundled in the JAR, call `LoadLibrary.initialize(path)` instead of `LoadLibrary.initializeFromResources()`. When the library is linked statically (e.g. in a GraalVM native image), call `LoadLibrary.assumeInitialized()` instead.

## Build

libsodiumjni is built with [Mill](https://mill-build.org), which compiles both the Java code and the C bindings, and generates the native libraries (`.so` / `.dylib` / `.dll`, and static `.a` / `.lib`).

Compile everything with
```text
$ ./mill __.compile
```

and run the tests with
```text
$ ./mill __.test
```

Requirements, besides a JDK (17 or later):
- Linux: gcc, and libsodium with its headers (e.g. `sudo apt-get install libsodium-dev`), found via `pkg-config` if installed
- macOS: the Xcode command line tools. libsodium is downloaded and built from source by the build, for the native library to target macOS 11.0 and later
- Windows: Visual Studio, with the C++ build tools for your CPU architecture. The build finds it with `vswhere`, or you can put the path of the `vcvars64.bat` script in the `VCVARSALL` environment variable. libsodium is downloaded by the build (official MSVC build)

The native libraries of all the supported platforms are built on the CI, and published to Maven Central from there. Releases are published from `vX.Y.Z` git tags. Other commits are published as `-SNAPSHOT` versions.

## Adding support for new libsodium methods

Typical workflow:
- add a `native` method in [`SodiumApi.java`](https://github.com/VirtusLab/libsodiumjni/blob/main/libsodiumjni/src/main/java/libsodiumjni/internal/SodiumApi.java)
- run `./mill __.compile`, which should add a corresponding declaration in [`libsodiumjni_internal_SodiumApi.h`](https://github.com/VirtusLab/libsodiumjni/blob/main/libsodiumjni/src/main/c/libsodiumjni_internal_SodiumApi.h). Commit that header along with your changes: the CI checks that it's up to date
- add an implementation for that declaration in [`libsodiumjni_internal_SodiumApi.c`](https://github.com/VirtusLab/libsodiumjni/blob/main/libsodiumjni/src/main/c/libsodiumjni_internal_SodiumApi.c), in the same directory. Check the lengths of the arrays it's passed, and whether they're null, before accessing them, and wipe the secrets it copies (see the existing functions)
- run `./mill __.compile` to ensure that the new C code compiles fine (warnings are errors, on Linux and macOS)
- use the newly added `native` method in [`Sodium.java`](https://github.com/VirtusLab/libsodiumjni/blob/main/libsodiumjni/src/main/java/libsodiumjni/Sodium.java)
- add tests for it, e.g. in [`SodiumTests.java`](https://github.com/VirtusLab/libsodiumjni/blob/main/libsodiumjni/src/test/java/libsodiumjni/tests/SodiumTests.java)
- ensure those tests pass when running `./mill __.test`
