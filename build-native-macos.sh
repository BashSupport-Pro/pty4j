#!/bin/bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

build_pty4j() {
  cd "$DIR/native"
  make -f Makefile clean x86_64
}

build_spawnhelper() {
  cd "$DIR/native/unix-spawn-helper"
  cmake .
  make clean
  make
}

build_pty4j
build_spawnhelper
