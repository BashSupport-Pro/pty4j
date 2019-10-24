#!/bin/bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

# buildDocker( GOOS GOARCH )
# shellcheck disable=SC2120
buildDocker() {
  local BUILD_DIR="$DIR/build"
  local SRC_DIR="$DIR"

  local DOCKER_IMAGE="${1}"
  [[ -z "$DOCKER_IMAGE" ]] && echo "Docker image not defined. Terminating." && exit 1

  local MAKE_TARGET="${2}"
  [[ -z "$MAKE_TARGET" ]] && echo "make target not defined. Terminating." && exit 1

  rm -rf "$BUILD_DIR"
  mkdir "$BUILD_DIR"
  cat >"$BUILD_DIR/run.sh" <<EOF
#!/bin/bash
set -e

echo "Updating packages..."
apt-get update -y
apt-get install -y build-essential gcc libc-dev

echo "Building native libs of pty4j-bashsupport for $MAKE_TARGET ..."
cd /src
cd native
make -f Makefile_linux "$MAKE_TARGET"
rm *.o
echo Successfully built pty4j-bashsupport...
echo
EOF
  chmod u+x "$BUILD_DIR/run.sh"

  docker run --rm -t \
    --mount "src=$BUILD_DIR,target=/build,type=bind" \
    --mount "src=$SRC_DIR,target=/src,type=bind" \
    "$DOCKER_IMAGE" /build/run.sh

  rm -rf "$BUILD_DIR"
}

# https://blog.hypriot.com/post/docker-intel-runs-arm-containers/
docker run --privileged hypriot/qemu-register

cd "$DIR"
buildDocker "ubuntu:xenial" linux_x86_64
buildDocker "ppc64le/ubuntu:xenial" linux_ppc64le
buildDocker "arm64v8/ubuntu:xenial" linux_aarch64

# https://github.com/hypriot/qemu-register doesn't support mips654 yet
#buildDocker "mips64le/debian:stable" linux_mips64el
