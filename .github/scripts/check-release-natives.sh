#!/usr/bin/env bash

# Checks that a release has the native libraries of all supported platforms, built for the right
# CPU architecture, both bundled in the JAR (loaded by LoadLibrary.initializeFromResources) and as
# separate artifacts (fetched by Scala CLI, with classifiers like aarch64-pc-linux).
#
# usage: check-release-natives.sh <libsodiumjni JAR> <artifacts directory>

set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <libsodiumjni JAR> <artifacts directory>" 1>&2
  exit 2
fi

jar="$1"
artifacts="$2"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$jar" 'META-INF/native/*' -d "$tmp" || true

status=0

# check <file> <glob pattern that the output of `file -b` must match>
check() {
  if [ ! -f "$1" ]; then
    echo "MISSING: $1"
    status=1
    return
  fi
  local desc
  desc="$(file -b "$1")"
  # shellcheck disable=SC2053
  if [[ "$desc" == $2 ]]; then
    echo "OK: $1: $desc"
  else
    echo "WRONG ARCHITECTURE OR FORMAT: $1: $desc (expected $2)"
    status=1
  fi
}

# classifier | directory under META-INF/native | shared library extension |
#   static library extension | expected `file -b` output of the shared library
while IFS='|' read -r classifier dir ext static_ext expected; do
  check "$tmp/META-INF/native/$dir/libsodiumjni.$ext" "$expected"
  check "$artifacts/libsodiumjni-$classifier.$ext" "$expected"
  if [ -f "$artifacts/libsodiumjni-$classifier.$static_ext" ]; then
    echo "OK: $artifacts/libsodiumjni-$classifier.$static_ext"
  else
    echo "MISSING: $artifacts/libsodiumjni-$classifier.$static_ext"
    status=1
  fi
done <<'EOF'
x86_64-pc-linux|linux64|so|a|ELF 64-bit LSB shared object, x86-64,*
aarch64-pc-linux|linux-aarch64|so|a|ELF 64-bit LSB shared object, ARM aarch64,*
x86_64-apple-darwin|darwin|dylib|a|Mach-O 64-bit*x86_64*
aarch64-apple-darwin|arm-darwin|dylib|a|Mach-O 64-bit*arm64*
x86_64-pc-win32|windows64|dll|lib|PE32+ executable*(DLL)*x86-64*
aarch64-pc-win32|windows-aarch64|dll|lib|PE32+ executable*(DLL)*Aarch64*
EOF

if [ "$status" -ne 0 ]; then
  echo "Some native libraries are missing or have the wrong architecture" 1>&2
fi
exit "$status"
