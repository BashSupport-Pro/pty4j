#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

BASHSUPPORT_DIR="$DIR/bashsupport-pro-link"
[[ ! -d "$BASHSUPPORT_DIR" ]] && echo "Unable to find BashSupport Pro base dir: $BASHSUPPORT_DIR" && exit 1

# shellcheck source=../bashsupport-pro-4.0/tools/shared.bash
source "$BASHSUPPORT_DIR/tools/shared.bash"

sign_macos() {
  read -rsp "Enter the password (macOS):" PASSWORD
  echo

  echo "Signing PTY4J library..."
  sign_notarize_binary "$DIR/os/darwin/libpty-bashpro.dylib" "$BASHSUPPORT_DIR/deployment/signing/libpty.entitlements"
  sign_notarize_binary "$DIR/os/darwin/pty4j-unix-spawn-helper" "$BASHSUPPORT_DIR/deployment/signing/pty4j-spawn-helper.entitlements"
}

sign_windows() {
  read -rsp "Enter the password (Windows):" PASSWORD
  echo

  local file outfile
  for file in "$DIR"/os/win/*/*; do
    echo "Signing pty4j $file..."
    outfile="$(dirname "$file")/signed-$(basename "$file")"
    sign "$PASSWORD" "$file" "$outfile"
    mv "$outfile" "$file"
  done
}

sign_macos

sign_windows

PUBLISHING_USER=jansorg gradle clean build testJar publishToMavenLocal
