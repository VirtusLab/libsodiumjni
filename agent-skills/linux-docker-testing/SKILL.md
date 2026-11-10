---
name: libsodiumjni-linux-docker-testing
description: Build and test libsodiumjni on Linux from a macOS machine, with Docker. Use when a change affects the native code, the native build, or Linux-specific behavior (glibc, symbols, linking), and the local machine isn't Linux. Also covers what can't be tested locally.
---

# Testing on Linux with Docker (libsodiumjni)

Native libraries are only built for the machine running the build, so Linux-specific behavior must be checked on
Linux. On macOS, use Docker (e.g. Colima: check `docker version` works, and ask the user before starting a VM).

## Image

The build needs a JDK 17, a C compiler and `make` (libsodium is built from source on Linux). Don't install libsodium:
the build mustn't depend on it.

```dockerfile
FROM eclipse-temurin:17-jdk-noble
RUN apt-get update && apt-get install -y --no-install-recommends gcc libc6-dev make binutils git rsync && rm -rf /var/lib/apt/lists/*
```

On Apple Silicon, this gives a Linux arm64 environment (like the `ubuntu-24.04-arm` CI runner). Linux x86_64 can be
run with `--platform linux/amd64` (emulated, much slower).

## Running

Run on a **fresh copy** of the repository, so that the host `out/` directory (macOS build outputs, Mill daemon state)
isn't shared:

```bash
docker run --rm -v "$PWD":/src:ro -v libsodiumjni-cache:/root/.cache <image> \
  bash -c 'rsync -a --exclude out --exclude .bsp --exclude .idea /src/ /work/ && cd /work && ./mill -i __.test'
```

- Colima only shares the user's home directory with its VM by default: files to mount must live under `$HOME`, not
  `/tmp` (mounting a `/tmp` path silently gives an empty directory).
- The cache volume keeps the Coursier / Mill downloads between runs.
- Useful tools in the container: `readelf -d` (dependencies), `nm -D --defined-only` (exported symbols),
  `objdump -T` (required glibc versions).

Remove the images and volumes created for the task once done.

## What can't be tested locally

Windows (x64 or arm64) can't be built or run on macOS / Linux: the Windows build needs Visual Studio. State in the PR
which platforms were actually checked, and that the CI is the first run for the others.
