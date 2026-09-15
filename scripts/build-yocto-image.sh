#!/usr/bin/env bash
set -eo pipefail

MACHINE="${1:?usage: $0 <machine> [image] [build-dir]}"
IMAGE="${2:-seeed-rockchip-image}"
BUILD_DIR="${3:-build-${MACHINE}}"
POKY_DIR="${POKY_DIR:-${PWD}/poky}"
BITBAKE_DIR="${BITBAKE_DIR:-${PWD}/bitbake}"
META_YOCTO_DIR="${META_YOCTO_DIR:-${PWD}/meta-yocto}"
LAYER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
META_OPENEMBEDDED_DIR="${META_OPENEMBEDDED_DIR:-${PWD}/meta-openembedded}"
META_ROCKCHIP_DIR="${META_ROCKCHIP_DIR:-${PWD}/meta-rockchip}"

if [[ ! -x "${POKY_DIR}/oe-init-build-env" ]]; then
    echo "POKY_DIR does not contain oe-init-build-env: ${POKY_DIR}" >&2
    exit 1
fi
if [[ ! -d "${BITBAKE_DIR}" ]]; then
    echo "BITBAKE_DIR does not exist: ${BITBAKE_DIR}" >&2
    exit 1
fi

for required_layer in \
    "${META_YOCTO_DIR}/meta-poky" \
    "${META_OPENEMBEDDED_DIR}/meta-oe" \
    "${META_ROCKCHIP_DIR}"; do
    if [[ ! -d "${required_layer}" ]]; then
        echo "Required layer is missing: ${required_layer}" >&2
        exit 1
    fi
done

# oe-init-build-env changes directory into the build directory.
source "${POKY_DIR}/oe-init-build-env" "${BUILD_DIR}" >/dev/null

bitbake-layers add-layer "${META_YOCTO_DIR}/meta-poky"
bitbake-layers add-layer "${META_OPENEMBEDDED_DIR}/meta-oe"
bitbake-layers add-layer "${META_ROCKCHIP_DIR}"
bitbake-layers add-layer "${LAYER_DIR}"

cat >> conf/local.conf <<EOF_CONF

MACHINE = "${MACHINE}"
DISTRO = "poky"
EOF_CONF

bitbake "${IMAGE}"
