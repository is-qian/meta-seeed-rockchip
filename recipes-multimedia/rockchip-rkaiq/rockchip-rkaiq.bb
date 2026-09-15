# Copyright (C) 2026 Seeed Studio
# Released under the MIT license (see COPYING.MIT for the terms)
#
# RKAIQ 3A engine for RK3588: deb_source tree cross-built with the same
# Arm GCC 8.3-2019.03 toolchain Rockchip used for the closed-source 3A
# algorithm blobs.
#
# Two failure classes ruled this combination in (see phase-7 notes):
#  1. Compiler generation: linking the closed .a with a newer GCC (poky
#     GCC 15 here, GCC 12 in Seeed's bookworm test) yields binaries that
#     run but whose AE converges badly - cold captures come out with
#     noise stripes and heavy overexposure.  Must use GCC 8.3.
#  2. Binary vintage: Seeed's published deb is a 2024-09 build that
#     predates the SCHED fallback (cd14f98) and the sensor-less seat guards
#     (f1e620a); without them the engine logs "stats disorder"
#     (AE open-loop) and its server SEGVs on the empty second ISP seat.
# Building the current deb_source tip (d0c0355, carrying both fixes)
# with the pinned toolchain reproduces what Seeed's CI (build-cross.sh)
# ships and the maintainer validated on this board.
#
# The cross toolchain tarball and the buster libdrm 2.4.97 sysroot bits
# are fetched with locked checksums, mirroring build-cross.sh: the
# closed .a require the same-generation compiler; bookworm libdrm pulls
# GLIBC_2.33 symbols which GCC 8.3 cannot link against.
# NOTE: rk3588 must NOT get -D_LARGEFILE64_SOURCE (it SIGSEGVs the 3A
# service on target; only rk3576 needs it for mmap64).

SUMMARY = "Rockchip RKAIQ camera engine for RK3588 (GCC 8.3 cross build)"
DESCRIPTION = "Rockchip ISP 3A runtime (closed algorithm blobs + server + IQ profiles) built from the deb_source tree with the pinned Arm GCC 8.3-2019.03 cross toolchain."
LICENSE = "CLOSED"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

PACKAGE_ARCH = "${MACHINE_ARCH}"
COMPATIBLE_MACHINE = "^recomputer-rk3588-devkit$"

SRC_URI = " \
    git://github.com/Panzhifeng-seeed/seeed_armbian_extension.git;protocol=https;nobranch=1;subpath=camera_engine_rkaiq;destsuffix=camera_engine_rkaiq \
    https://github.com/armbian/mirror/releases/download/_toolchain/gcc-arm-8.3-2019.03-x86_64-aarch64-linux-gnu.tar.xz;name=gcc83;downloadfilename=gcc-arm-8.3-2019.03-x86_64-aarch64-linux-gnu.tar.xz;subdir=gcc83 \
    http://archive.debian.org/debian/pool/main/libd/libdrm/libdrm-dev_2.4.97-1_arm64.deb;name=drmdev;downloadfilename=libdrm-dev_2.4.97-1_arm64.deb;subdir=drmsysroot \
    http://archive.debian.org/debian/pool/main/libd/libdrm/libdrm2_2.4.97-1_arm64.deb;name=drmlib;downloadfilename=libdrm2_2.4.97-1_arm64.deb;subdir=drmsysroot \
    file://rkaiq_3A.service \
"
SRC_URI[gcc83.sha256sum] = "8ce3e7688a47d8cd2d8e8323f147104ae1c8139520eca50ccf8a7fa933002731"
SRC_URI[drmdev.sha256sum] = "21bd19c7e4ad86898e3613f45683630f5f42cd696b978ebced0b93a1d943104a"
SRC_URI[drmlib.sha256sum] = "c1521eb6b63dc794e9487e6fd6956201b97fc230d514ccf5bf767111e0f86efb"
# deb_source tree tip: carries cd14f98 (SCHED_OTHER fallback, mandatory
# on system shutdown), d9d2f21 (imx708 IQ), 07bebb0 (unit stop cap) and the
# f1e620a server guards (sensor-less ISP seats, closed-lib SEGV
# recovery).  Same revision the deb and the RK3576 recipe build from.
SRCREV = "d0c035512c92e6d82eb4b0f3d4cc7bdba75c0384"

S = "${UNPACKDIR}/camera_engine_rkaiq"

PV = "1.0"
PR = "r2"

DEPENDS = "cmake-native xxd-native chrpath-replacement-native"

# The splits must precede PN in PACKAGES: usrmerge puts the systemd unit
# under /usr/lib/systemd/system, which PN's ${libdir} glob would otherwise
# claim (enablement is handled by the class preset at rootfs time).
PACKAGE_BEFORE_PN = "${PN}-server ${PN}-iqfiles"

RDEPENDS:${PN}-server = "${PN} libdrm"
# The prelinked payload needs libdrm.so.2 at runtime (buster-built code
# runs fine against the image's newer libdrm).
RDEPENDS:${PN} = "libdrm"

SYSTEMD_PACKAGES = "${PN}-server"
SYSTEMD_SERVICE:${PN}-server = "rkaiq_3A.service"
SYSTEMD_AUTO_ENABLE:${PN}-server = "enable"

inherit systemd

# The toolchain's absolute paths end up in the Release objects; keep the
# payload unstripped (never validated stripped) and relax buildpaths QA
# for this externally-built binary set.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} += "buildpaths already-stripped"
INSANE_SKIP:${PN}-server += "buildpaths already-stripped"

do_compile() {
    # The external GCC 8.3 must not see the OE build flags (it predates
    # options like -fcanon-prefix-map); build-cross.sh passes none.
    unset CFLAGS CXXFLAGS LDFLAGS CPPFLAGS

    # BitBake's unpack already extracted both the toolchain tarball and
    # the two libdrm debs under their subdirs; use them in place.
    GCC83=${UNPACKDIR}/gcc83/gcc-arm-8.3-2019.03-x86_64-aarch64-linux-gnu
    SYSROOT=${UNPACKDIR}/drmsysroot
    ln -sfn libdrm ${SYSROOT}/usr/include/drm

    # 3. CMake toolchain file (same shape as build-cross.sh).
    cat > ${WORKDIR}/gcc83-toolchain.cmake <<CTEOF
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER   ${GCC83}/bin/aarch64-linux-gnu-gcc)
set(CMAKE_CXX_COMPILER ${GCC83}/bin/aarch64-linux-gnu-g++)
list(APPEND CMAKE_FIND_ROOT_PATH ${SYSROOT} ${GCC83}/aarch64-linux-gnu)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
set(SYSROOT_LIBDIR ${SYSROOT}/usr/lib/aarch64-linux-gnu)
set(CMAKE_EXE_LINKER_FLAGS "-Wl,-rpath-link,${SYSROOT_LIBDIR} -L${SYSROOT_LIBDIR}")
set(CMAKE_SHARED_LINKER_FLAGS "-L${SYSROOT_LIBDIR}")
CTEOF

    # 4. Cross-configure and build (debian/rules.in knobs, verbatim).
    # The tree launches every compile/link through ccache when found;
    # there is no ccache in the task PATH and the launches die with 127,
    # so drop the RULE_LAUNCH lines (plain compiles, like a pristine CI
    # container without ccache).
    sed -i '/RULE_LAUNCH_COMPILE ccache/d;/RULE_LAUNCH_LINK ccache/d' \
        ${S}/rkaiq/cmake/CompileOptions.cmake

    # Start from a clean build dir: a failed configure leaves the OE
    # CFLAGS baked into CMakeCache.txt and they poison every retry.
    rm -rf ${WORKDIR}/build-gcc83
    cmake -B ${WORKDIR}/build-gcc83 -S ${S} \
        -DCMAKE_TOOLCHAIN_FILE=${WORKDIR}/gcc83-toolchain.cmake \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX=/usr \
        -DRKAIQ_TARGET_SOC=rk3588 \
        -DRKAIQ_ENABLE_LIBDRM=ON \
        -DRKAIQ_ENABLE_AF=ON \
        -DRKAIQ_HAVE_MULTIISP=ON \
        -DLIBDRM_LIBRARY=${SYSROOT}/usr/lib/aarch64-linux-gnu/libdrm.so \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
        -DCMAKE_C_COMPILER_LAUNCHER= \
        -DCMAKE_CXX_COMPILER_LAUNCHER= \
        -DRKAIQ_J2S4B_DEV=OFF
    # Build only the closed-payload library through cmake: the debug
    # tools (media_enquiry, rkaiq_tool_server) need libraries this
    # minimal sysroot does not carry, and the tree's server link rule
    # drops libdrm from the line (unresolved drm* from librkaiq.so).
    cmake --build ${WORKDIR}/build-gcc83 -j $(nproc) --target rkaiq

    # Rebuild the open-source server wrapper by hand (three translation
    # units, same list as the RK3576 recipe and the in-tree CMake
    # target), linked against the freshly built librkaiq.so with the
    # sysroot libdrm.
    XG=${GCC83}/bin/aarch64-linux-gnu-gcc
    XGXX=${GCC83}/bin/aarch64-linux-gnu-g++
    LIBDIR=${WORKDIR}/build-gcc83/rkaiq/all_lib/Release
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
    ${XG} -g -O2 -DADD_RK_AIQ -std=gnu11 -D_DEFAULT_SOURCE ${INC} -include sys/time.h -c \
        ${S}/rkaiq/common/mediactl/mediactl.c -o ${WORKDIR}/mediactl.o
    ${XG} -g -O2 -DADD_RK_AIQ -std=gnu11 -D_DEFAULT_SOURCE ${INC} -include sys/time.h -c \
        ${S}/rkaiq/xcore/xcam_log.c -o ${WORKDIR}/xcam_log.o
    ${XGXX} -g -O2 -std=c++11 -DADD_RK_AIQ ${INC} -include sys/time.h -c \
        ${S}/rkaiq_3A_server/rkaiq_3A_server.cpp -o ${WORKDIR}/rkaiq_3A_server.o
    ${XGXX} ${WORKDIR}/rkaiq_3A_server.o ${WORKDIR}/mediactl.o \
        ${WORKDIR}/xcam_log.o -o ${WORKDIR}/rkaiq_3A_server \
        -L${LIBDIR} -lrkaiq -L${SYSROOT}/usr/lib/aarch64-linux-gnu -ldrm \
        -lpthread -ldl -Wl,-rpath-link,${SYSROOT}/usr/lib/aarch64-linux-gnu
}

do_install() {
    install -d ${D}${libdir} ${D}${bindir} ${D}${sysconfdir}/iqfiles \
        ${D}${systemd_system_unitdir}

    # The closed payload is self-contained: librkaiq.so NEEDEDs only
    # system libraries plus libdrm, so the companion libs the deb also
    # ships (IspFec/smartIr/rkrawstream, tool-facing) are not needed on
    # the 3A path and are not built here (cmake --install would also
    # fail staging IspFec when only the rkaiq target ran).
    install -m 0644 \
        ${WORKDIR}/build-gcc83/rkaiq/all_lib/Release/librkaiq.so \
        ${D}${libdir}/
    install -m 0755 ${WORKDIR}/rkaiq_3A_server ${D}${bindir}/
    # The cmake link embeds build-tree RPATHs; strip them so the
    # packaged binaries resolve libraries purely via the image layout.
    chrpath -d ${D}${libdir}/librkaiq.so ${D}${bindir}/rkaiq_3A_server

    # Layer's hardened native unit (the tree ships a sysv wrapper).
    install -m 0644 ${UNPACKDIR}/rkaiq_3A.service \
        ${D}${systemd_system_unitdir}/

    # IQ profiles: rk3588 uses the isp3x set (same files the deb ships).
    install -m 0644 ${S}/rkaiq/iqfiles/isp3x/*.json ${D}${sysconfdir}/iqfiles/
}

FILES:${PN}-server = " \
    ${bindir}/rkaiq_3A_server \
    ${systemd_system_unitdir}/rkaiq_3A.service \
"
FILES:${PN}-iqfiles = "${sysconfdir}/iqfiles"

# The build ships unversioned .so files: they are the runtime libraries,
# claimed for PN explicitly so the default -dev split does not grab
# them; no headers ship, leaving -dev empty on purpose.
FILES:${PN} = "${libdir}/librkaiq.so*"
FILES:${PN}-dev = ""

# Headers, .a archives and debug tools are not shipped.
