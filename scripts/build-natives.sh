#!/usr/bin/env bash
# Builds libghostty-vt for every supported platform into src/main/resources/native/<os>-<arch>/.
# Cross-compiles all four targets from one host with Zig; the Ghostty commit and Zig version are pinned.
set -euo pipefail

GHOSTTY_REPO="https://github.com/ghostty-org/ghostty.git"
GHOSTTY_COMMIT="4ae9f1a2de5484de3d6a13fe03676b8853b9c41c"
ZIG_VERSION="0.16.0"

root="$(cd "$(dirname "$0")/.." && pwd)"
cache="$root/.cache"
mkdir -p "$cache"

host_os="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$host_os" in darwin) host_os="macos" ;; linux) ;; *) echo "unsupported host $host_os" >&2; exit 1 ;; esac
host_arch="$(uname -m)"
case "$host_arch" in arm64) host_arch="aarch64" ;; amd64) host_arch="x86_64" ;; esac

zig_dir="$cache/zig-$host_arch-$host_os-$ZIG_VERSION"
if [[ ! -x "$zig_dir/zig" ]]; then
  archive="zig-$host_arch-$host_os-$ZIG_VERSION.tar.xz"
  curl -fsSL "https://ziglang.org/download/$ZIG_VERSION/$archive" -o "$cache/$archive"
  tar -xJf "$cache/$archive" -C "$cache"
  rm "$cache/$archive"
fi
zig="$zig_dir/zig"

src="$cache/ghostty"
if [[ ! -d "$src/.git" ]]; then
  git clone --filter=blob:none "$GHOSTTY_REPO" "$src"
fi
git -C "$src" fetch --quiet origin "$GHOSTTY_COMMIT"
git -C "$src" checkout --quiet --detach "$GHOSTTY_COMMIT"

build() {
  local target="$1" platform="$2" file="$3"
  local prefix="$cache/out/$platform"
  (cd "$src" && "$zig" build -Demit-lib-vt=true -Doptimize=ReleaseFast -Dtarget="$target" --prefix "$prefix")
  local dest="$root/src/main/resources/native/$platform"
  mkdir -p "$dest"
  cp -L "$prefix/lib/$file" "$dest/$file"
  echo "built $dest/$file"
}

build aarch64-macos darwin-aarch64 libghostty-vt.dylib
build x86_64-macos darwin-x86_64 libghostty-vt.dylib
build x86_64-linux-gnu linux-x86_64 libghostty-vt.so
build aarch64-linux-gnu linux-aarch64 libghostty-vt.so
