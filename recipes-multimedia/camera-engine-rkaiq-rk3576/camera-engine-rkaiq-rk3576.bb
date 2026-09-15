# Copyright (C) 2026 Seeed Studio
# Released under the MIT license (see COPYING.MIT for the terms)
#
# RKAIQ 3A engine for RK3576: closed libs from Seeed's prebuilt deb,
# rkaiq_3A_server rebuilt from the deb_source source tree.
#
# The source-built rockchip-rkaiq recipe cannot serve this machine: the
# rk3576-side closed-source 3A algorithm .a blobs in the deb_source source
# tree are still broken when linked with the Wrynose toolchain (RK3588
# phase-5 leftover #11), and Seeed's own deb is deliberately built with the
# Arm GCC 8.3 toolchain "required by closed-source 3A algorithm libraries".
# The deb ships fully prelinked binaries/libraries, so no host linking is
# needed for them and the runtime only requires glibc/libstdc++/libdrm
# (already in the image via gstreamer/mpp).  Until the rk3576 blobs are
# fixed upstream, the deb is the supported distribution channel:
#   https://github.com/Seeed-Studio/seeed_armbian_extension/tree/deb_source/prebuilt
#
# The deb's own rkaiq_3A_server binary predates the deb_source fixes for
# this board and crash-loops on the RK3576 split media topology (sensor
# lives in the rkcif graph; the closed lib only reports FakeCamera):
# commit f1e620a "fix: forward sensor exposure control for RK3576 virtual
# ISP" teaches the - open-source - server wrapper to resolve the real
# sensor entity from the CIF graphs, pass it to rk_aiq_uapi2_sysctl_init
# (loads the genuine imx708 IQ), recover from SEGV inside the closed lib
# and forward a fixed exposure (2000/8000) to the real sensor on stream
# start.  We therefore rebuild just the server from the same tree (tip
# d0c0355, identical to the RK3588 source recipe) and link it against the
# deb's prelinked librkaiq.so (CODEABI_3.8 symbols match), keeping the
# closed payload untouched.
#
# NOTE the kernel counterpart: the uapi alignment patch guarding
# ISP2X_MESH_BUF_NUM=2 in linux-seeed (files/0004 of the kernel
# recipe).  The deb's librkaiq issues RKISP_CMD_GET_MESHBUF_INFO with the
# 2-entry struct layout; against a kernel built with 3 the command number
# mismatches and the CAC mesh-buffer GET fails with ENOTTY, which kills
# the old server with a NULL deref.
#
# The IQ profiles cover both board camera seats (imx219 Pi Camera v2 /
# imx708 Pi Camera v3 + DW9817 VCM), matching the cam dtsi.
#
# TODO(upstream): switch back to a source-built recipe for rk3576 once the
# prebuilt .a blobs link cleanly; keep SRCREV provenance from the deb
# branch (camera-engine-rkaiq-rk3576_1.0-1, sha256 below).

SUMMARY = "Rockchip RKAIQ camera engine for RK3576 (prebuilt libs + source server)"
DESCRIPTION = "Rockchip ISP 3A runtime libraries from Seeed's prebuilt deb, with the rkaiq_3A_server wrapper rebuilt from the deb_source tree for the RK3576 split media topology, plus camera tuning profiles for the RK3576 SoC."
LICENSE = "CLOSED"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

PACKAGE_ARCH = "${MACHINE_ARCH}"
COMPATIBLE_MACHINE = "^recomputer-rk3576-devkit$"

SRC_URI = "https://raw.githubusercontent.com/Seeed-Studio/seeed_armbian_extension/deb_source/prebuilt/camera-engine-rkaiq-rk3576_1.0-1_arm64.deb;downloadfilename=camera-engine-rkaiq-rk3576_1.0-1_arm64.deb;subdir=rkaiq-deb \
    git://github.com/Panzhifeng-seeed/seeed_armbian_extension.git;protocol=https;nobranch=1;subpath=camera_engine_rkaiq;destsuffix=camera_engine_rkaiq \
    file://rkaiq_3A.service \
"
SRC_URI[sha256sum] = "879c9925f8eae1e1b03075e9ed73770c8f99323de095a0c11f1843d45ba4b59b"
# deb_source branch tip; identical to the RK3588 source-built recipe.
SRCREV = "d0c035512c92e6d82eb4b0f3d4cc7bdba75c0384"

S = "${UNPACKDIR}/camera_engine_rkaiq"

PV = "1.0"
PR = "r1"

# The splits must precede PN in PACKAGES: usrmerge puts the systemd unit
# under /usr/lib/systemd/system, which PN's ${libdir} glob would otherwise
# claim (enablement is handled by the class preset at rootfs time).
PACKAGE_BEFORE_PN = "${PN}-server ${PN}-iqfiles"

RDEPENDS:${PN}-server = "${PN}"
# Explicit: the prelinked librkaiq.so needs libdrm.so.2, and file-rdeps
# QA cannot auto-resolve it from a repackaged binary.
RDEPENDS:${PN} = "libdrm"

SYSTEMD_PACKAGES = "${PN}-server"
SYSTEMD_SERVICE:${PN}-server = "rkaiq_3A.service"
SYSTEMD_AUTO_ENABLE:${PN}-server = "enable"

inherit systemd

# Rebuild only the open-source server wrapper: the three translation
# units rkaiq_3A_server.cpp, the bundled libmediactl and xcam_log are
# enough (mirrors the in-tree CMake target's source list), linked against
# the deb's prelinked librkaiq.so instead of the still-broken .a blobs.
do_compile() {
    INC=" \
        -I${S}/rkaiq/include \
        -I${S}/rkaiq/include/uAPI2 \
        -I${S}/rkaiq/include/common/mediactl \
        -I${S}/rkaiq/include/xcore \
        -I${S}/rkaiq/include/xcore/base \
        -I${S}/rkaiq_3A_server/common/mediactl \
        -I${S}/rkaiq_3A_server \
        -I${S}/rkaiq \
        -I${S}/rkaiq/common/mediactl \
        -I${S}/rkaiq/xcore \
        -I${S}/rkaiq/xcore/base \
    "
    ${CC} ${CFLAGS} -DADD_RK_AIQ ${INC} -c \
        ${S}/rkaiq/common/mediactl/mediactl.c -o ${WORKDIR}/mediactl.o
    ${CC} ${CFLAGS} -DADD_RK_AIQ ${INC} -c \
        ${S}/rkaiq/xcore/xcam_log.c -o ${WORKDIR}/xcam_log.o
    ${CXX} ${CXXFLAGS} -std=c++11 -DADD_RK_AIQ ${INC} -c \
        ${S}/rkaiq_3A_server/rkaiq_3A_server.cpp -o ${WORKDIR}/rkaiq_3A_server.o
    ${CXX} ${LDFLAGS} ${WORKDIR}/rkaiq_3A_server.o ${WORKDIR}/mediactl.o \
        ${WORKDIR}/xcam_log.o -o ${WORKDIR}/rkaiq_3A_server \
        -L${UNPACKDIR}/rkaiq-deb/usr/lib -lrkaiq -lpthread -ldl
}

# BitBake's unpack extracts the deb archive straight into
# ${UNPACKDIR}/rkaiq-deb (etc/lib/usr); the closed payload is only
# reshuffled, while the server binary comes from do_compile above.
do_install() {
    EX=${UNPACKDIR}/rkaiq-deb

    # Runtime libraries (prelinked, closed 3A blobs already inside).
    install -d ${D}${libdir}
    install -m 0644 ${EX}/usr/lib/librkaiq.so* ${D}${libdir}/
    install -m 0644 ${EX}/usr/lib/libIspFec.so* ${D}${libdir}/
    install -m 0644 ${EX}/usr/lib/libsmartIr.so* ${D}${libdir}/
    install -m 0644 ${EX}/usr/lib/librkrawstream.so* ${D}${libdir}/

    # 3A server: rebuilt from the deb_source tree (FakeCamera resolution,
    # SEGV recovery, sensor exposure forwarding) with our hardened unit.
    install -d ${D}${bindir} ${D}${systemd_system_unitdir}
    install -m 0755 ${WORKDIR}/rkaiq_3A_server ${D}${bindir}/
    install -m 0644 ${UNPACKDIR}/rkaiq_3A.service \
        ${D}${systemd_system_unitdir}/

    # IQ profiles: flat json set, covering imx219/imx708 (both cam seats).
    install -d ${D}${sysconfdir}/iqfiles
    install -m 0644 ${EX}/etc/iqfiles/*.json ${D}${sysconfdir}/iqfiles/
}

FILES:${PN}-server = " \
    ${bindir}/rkaiq_3A_server \
    ${systemd_system_unitdir}/rkaiq_3A.service \
"
FILES:${PN}-iqfiles = "${sysconfdir}/iqfiles"

# The deb ships unversioned .so files (no soname suffixes, no symlinks):
# they are the runtime libraries, so claim them for PN explicitly or the
# default -dev split grabs them and QA rejects non-symlink .so in -dev.
# No headers ship, leaving -dev empty on purpose.
FILES:${PN} = " \
    ${libdir}/librkaiq.so* \
    ${libdir}/libIspFec.so* \
    ${libdir}/libsmartIr.so* \
    ${libdir}/librkrawstream.so* \
"
FILES:${PN}-dev = ""

# The .a archives, headers, dumpcam/rkaiq_tool_server debugging tools and
# the deb's sysv/init scripts are not shipped; the source recipe drops the
# same tool set on RK3588.
