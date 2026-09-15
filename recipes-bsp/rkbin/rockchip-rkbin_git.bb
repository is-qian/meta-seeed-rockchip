DESCRIPTION = "Rockchip firmware and tool binaries from the official rkbin repository"
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://LICENSE;md5=11e3673115959bf596feaaa6ea7ce9a5"

inherit deploy

# Pinned to the last upstream revision that still carries the exact blob
# versions this boot chain was validated with (RK3588: BL31 v1.54 /
# BL32 v1.20 / DDR v1.21, RK3576: BL31 v1.24 / BL32 v1.08 / DDR v1.12).
# Upstream master removes old blob versions as new ones land (the first
# of these was dropped on 2026-01-07); moving this pin silently changes
# the boot chain and requires on-board revalidation.  Armbian pins the
# same way per SoC for the same reason (their rk3576 FIXME documents a
# newer DDR regressing boot on some boards).
SRC_URI = "git://github.com/rockchip-linux/rkbin.git;protocol=https;nobranch=1 \
           file://boot_merger"
SRCREV = "a6f2a6486d5c5ce0506c57e795a27022e1687ce7"

PROVIDES += "trusted-firmware-a optee-os"
INHIBIT_DEFAULT_DEPS = "1"
PACKAGE_ARCH = "${MACHINE_ARCH}"
COMPATIBLE_MACHINE = "^recomputer-"

PACKAGES = "${PN}"
ALLOW_EMPTY:${PN} = "1"

do_install() {
    :
}

# Exact filenames, no version globs: a pin move must touch these lines
# explicitly instead of silently picking up whatever the tree carries.
do_deploy() {
    install -Dm0644 ${S}/bin/rk35/rk3588_bl31_v1.54.elf \
        ${DEPLOYDIR}/bl31-rk3588.elf
    install -Dm0644 ${S}/bin/rk35/rk3588_bl32_v1.20.bin \
        ${DEPLOYDIR}/tee-rk3588.bin
    install -Dm0644 ${S}/bin/rk35/rk3588_ddr_lp4_2112MHz_lp5_2400MHz_v1.21.bin \
        ${DEPLOYDIR}/ddr-rk3588.bin
    # Prebuilt maskrom usbplug consumed by the RK3588 loader image build
    # (RK3576 builds its usbplug from U-Boot source instead, see the
    # u-boot bbappend).
    install -Dm0644 ${S}/bin/rk35/rk3588_usbplug_v1.11.bin \
        ${DEPLOYDIR}/usbplug-rk3588.bin
    # The staged SDK tools tree ships the v1.35 boot_merger (upstream
    # introduced it in 2024-02 and has since moved on); ship the exact
    # build the current maskrom loader images were produced with.
    install -Dm0755 ${UNPACKDIR}/boot_merger ${DEPLOYDIR}/boot_merger
}

do_deploy:append:recomputer-rk3576-devkit() {
    # RK3576 blobs: DDR v1.12 + BL31 v1.24 is the combination Seeed's armbian
    # mainline-U-Boot path uses on this board.  (The armbian vendor branch
    # deliberately stays on DDR v1.08 because v1.09 regressed on some boards;
    # v1.12 is the next good generation and ships in the pinned rkbin.)
    install -Dm0644 ${S}/bin/rk35/rk3576_bl31_v1.24.elf \
        ${DEPLOYDIR}/bl31-rk3576.elf
    install -Dm0644 ${S}/bin/rk35/rk3576_bl32_v1.08.bin \
        ${DEPLOYDIR}/tee-rk3576.bin
    install -Dm0644 ${S}/bin/rk35/rk3576_ddr_lp4_2112MHz_lp5_2736MHz_v1.12.bin \
        ${DEPLOYDIR}/ddr-rk3576.bin
    # RK3576 idbloader is a three-segment RKSD image whose first segment is
    # the SRAM "boost" stage (0x3FFC0000), ahead of the DDR init and SPL.
    install -Dm0644 ${S}/bin/rk35/rk3576_boost_v1.03.bin \
        ${DEPLOYDIR}/boost-rk3576.bin
}

addtask deploy after do_install
