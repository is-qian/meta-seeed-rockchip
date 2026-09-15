# Common definition for the Seeed reComputer vendor Linux kernel.  The
# recipes (linux-seeed_6.1.bb) exist once for all reComputer machines; each
# machine only appends its own board files through the per-machine
# SRC_URI:append:recomputer-* blocks at the bottom of this file.

SUMMARY = "Seeed reComputer vendor Linux kernel (Armbian rk-6.1-rkr7.2)"
DESCRIPTION = "Linux 6.1 vendor kernel for the Seeed reComputer DevKits, \
taken from Armbian's linux-rockchip repository. Board DTS files, the \
fiq-debugger console fixes and the SoC/board config deltas are layered on \
top."
SECTION = "kernel"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel
inherit kernel-yocto

COMPATIBLE_MACHINE = "^recomputer-"

# Armbian's vendor branch for all rk35xx SoCs; the same tree feeds every
# reComputer machine.  Pinned to the branch tip verified for this switch.
# Shared per-tree files: the fiq-debugger console fixes and the common
# hardware config baseline; each machine appends its own DTS, cam dtsi,
# config delta and (where needed) extra patches below.
SRC_URI = "git://github.com/armbian/linux-rockchip.git;protocol=https;branch=rk-6.1-rkr7.2 \
    file://recomputer-hardware.cfg \
    file://0001-fiq-debugger-keep-console-on-break.patch \
    file://0002-fiq-debugger-console-thread-lost-wakeup.patch \
    file://0003-fiq-debugger-lossless-tty-wake.patch \
"
SRCREV = "5ef479b1070b9d74dcefce859826176ce0eb5fe1"

PV = "6.1+armbian-rkr7.2"
KERNEL_VERSION_SANITY_SKIP = "1"

# The generic vendor defconfig enables drivers for many unrelated SoCs.  The
# Rockchip defconfig keeps the module set aligned with this 6.1 vendor tree
# and avoids compiling incompatible DRM drivers from other platforms.
KBUILD_DEFCONFIG = "rockchip_linux_defconfig"
# kernel-yocto's configme falls back to merge_config.sh -n (allnoconfig)
# when KCONFIG_MODE is unset and a defconfig file is present in UNPACKDIR.
# allnoconfig forces every symbol not explicitly listed in the defconfig to
# 'n' instead of its Kconfig default; CONFIG_TTY (which rockchip_linux_defconfig
# never lists because it defaults to y) gets disabled that way, which drops
# the whole serial subsystem (SERIAL_8250 depends on TTY) and produces a
# silent kernel.  Use alldefconfig so unlisted symbols keep their defaults.
KCONFIG_MODE = "alldefconfig"
KERNEL_IMAGETYPE = "Image"
# KERNEL_DEVICETREE stays per machine (set in the machine confs); it is
# deliberately not set here so a future machine cannot silently inherit
# another board's DTB.

# Per-machine additions: board DTS pair, config delta and SoC-specific
# patches.  Filenames follow the ${RK_SOC_FAMILY}-${MACHINE}.dts and
# ${MACHINE}-cam.dtsi patterns.
SRC_URI:append:recomputer-rk3588-devkit = " \
    file://recomputer-rk3588-extra.cfg \
    file://rk3588-recomputer-rk3588-devkit.dts \
    file://recomputer-rk3588-devkit-cam.dtsi \
"
SRC_URI:append:recomputer-rk3576-devkit = " \
    file://recomputer-rk3576-extra.cfg \
    file://rk3576-recomputer-rk3576-devkit.dts \
    file://recomputer-rk3576-devkit-cam.dtsi \
    file://0004-uapi-rkisp-mesh-buf-num-2.patch \
"

# The vendor tree is SoC-complete but does not carry Seeed's board DTS. Add
# only the board files and DTB registration to the source tree at configure
# time (installed from UNPACKDIR so bitbake's stamp tracking sees them).
do_configure:prepend() {
    install -Dm0644 ${UNPACKDIR}/${RK_SOC_FAMILY}-${MACHINE}.dts \
        ${S}/arch/arm64/boot/dts/rockchip/${RK_SOC_FAMILY}-${MACHINE}.dts
    install -Dm0644 ${UNPACKDIR}/${MACHINE}-cam.dtsi \
        ${S}/arch/arm64/boot/dts/rockchip/${MACHINE}-cam.dtsi
    if ! grep -q '${RK_SOC_FAMILY}-${MACHINE}.dtb' \
        ${S}/arch/arm64/boot/dts/rockchip/Makefile; then
        printf '%s\n' 'dtb-$(CONFIG_ARCH_ROCKCHIP) += ${RK_SOC_FAMILY}-${MACHINE}.dtb' \
            >> ${S}/arch/arm64/boot/dts/rockchip/Makefile
    fi
}

# Wrynose's buildpaths QA also scans generated kernel source files. These two
# Linux generators embed the absolute kernel-source path in a comment, which
# compiler prefix maps cannot rewrite because it is generated source text.
# Replace the build-root portion after generation so linux-*-src remains
# relocatable and passes the package QA check.
do_compile:append() {
    for generated in \
        ${B}/drivers/tty/vt/consolemap_deftbl.c \
        ${B}/lib/oid_registry_data.c; do
        if [ -f "${generated}" ]; then
            sed -i "s#${TMPDIR}#<yocto-tmpdir>#g" "${generated}"
        fi
    done
}

do_compile_kernelmodules:append() {
    for generated in \
        ${B}/drivers/tty/vt/consolemap_deftbl.c \
        ${B}/lib/oid_registry_data.c; do
        if [ -f "${generated}" ]; then
            sed -i "s#${TMPDIR}#<yocto-tmpdir>#g" "${generated}"
        fi
    done
}

# kernel-yocto's scc does not create its output directories: do_kernel_metadata
# (patch mode) and the config-mode metadata run embedded in do_kernel_configme
# write straight into ${S}/.kernel-meta, and do_kernel_configme itself
# redirects merge_config.sh output into ${S}/.kernel-meta/cfg, all of which
# fail with "Directory nonexistent" when nothing has created them yet.
# mkdir -p is idempotent even when several machines build from this tree.
do_kernel_metadata:prepend() {
    mkdir -p ${S}/.kernel-meta ${S}/.kernel-meta/cfg
}

do_kernel_configme:prepend() {
    mkdir -p ${S}/.kernel-meta ${S}/.kernel-meta/cfg
}
