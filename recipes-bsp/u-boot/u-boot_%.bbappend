FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI = "git://github.com/radxa/u-boot.git;protocol=https;branch=next-dev-v2024.10"
SRCREV = "39cd993e5d6296635438e84f4576b3a9bf76f86e"
LIC_FILES_CHKSUM = "file://Licenses/README;md5=a2c678cfd4a4d97135585cad908541c6"

SRC_URI:append = " \
    file://${MACHINE}_defconfig \
    file://${RK_SOC_FAMILY}-${MACHINE}.dts \
    file://${RK_SOC_FAMILY}-maskrom.ini \
    file://0002-rk3588-charge-animation-initialize-status.patch \
    file://0001-rockchip-use-python3-for-fit-generator.patch \
    file://0100-rkflash-sfc-unaligned-write.patch \
    file://0102-fit-restore-optee-node.patch \
    file://0103-spi-nor-ids-carry-zbit-entries.patch \
"
SRC_URI:append:rk3576 = " \
    file://rk3576-usbplug-board.config \
    file://0005-rk3576-usbplug-guard-scsi-bootdev.patch \
    file://0101-usbplug-defconfig-spi-flash-vendors.patch \
"

# Firmware and loader tooling come from the pinned official rkbin recipe;
# the Rockchip FIT generator consumes them from fixed file names in its
# working directory, so keep the source paths explicit to stay tied to
# the pinned rkbin deployment rather than a moving fetch.
BL31 = "${DEPLOY_DIR_IMAGE}/bl31-${RK_SOC_FAMILY}.elf"
RK_TEE = "${DEPLOY_DIR_IMAGE}/tee-${RK_SOC_FAMILY}.bin"
RK_BOOT_MERGER = "${DEPLOY_DIR_IMAGE}/boot_merger"
do_compile[depends] += "rockchip-rkbin:do_deploy"
# RK3588 uses the rkbin prebuilt usbplug; RK3576 rebuilds CODE472 from
# this tree instead (see do_compile): the prebuilt (v1.04) runs UFS link
# training before serving USB (~20 s of UIC timeouts, which makes
# `upgrade_tool db` fail) and does not know the board NOR's ZBIT
# ZB25LQ128 JEDEC id.  Seeed's Armbian integration compiles the plug from
# source for the same reasons (RK_COMPILE_USBPLUG=yes).
RK_USBPLUG:rk3588 = "${DEPLOY_DIR_IMAGE}/usbplug-rk3588.bin"
RK_USBPLUG:rk3576 = "${WORKDIR}/usbplug-build/usbplug.bin"

do_configure:prepend() {
    install -Dm0644 ${UNPACKDIR}/${MACHINE}_defconfig \
        ${S}/configs/${UBOOT_MACHINE}
    install -Dm0644 ${UNPACKDIR}/${RK_SOC_FAMILY}-${MACHINE}.dts \
        ${S}/arch/arm/dts/${RK_SOC_FAMILY}-${MACHINE}.dts
    if [ -n "${UBOOT_EXTRA_CONFIG}" ] && [ -f "${UBOOT_EXTRA_CONFIG}" ]; then
        cat "${UBOOT_EXTRA_CONFIG}" >> ${S}/configs/${UBOOT_MACHINE}
    fi
}

do_compile:append() {
    install -d ${B}/arch/arm/mach-rockchip
    ln -sf ${S}/arch/arm/mach-rockchip/fit_nodes.sh \
        ${B}/arch/arm/mach-rockchip/fit_nodes.sh
    ln -sf ${S}/arch/arm/mach-rockchip/fit_args.sh \
        ${B}/arch/arm/mach-rockchip/fit_args.sh
    ln -sf ${S}/arch/arm/mach-rockchip/decode_bl31.py \
        ${B}/arch/arm/mach-rockchip/decode_bl31.py

    # make_fit_atf.sh is a vendor script: decode_bl31.py looks specifically
    # for ./bl31.elf and fit_nodes.sh includes ./tee.bin.  BL31 alone is not
    # sufficient when the generator is called directly from this recipe.
    install -Dm0644 "${BL31}" ${B}/bl31.elf
    install -Dm0644 "${RK_TEE}" ${B}/tee.bin
    cd ${B}
    srctree=. ${S}/arch/arm/mach-rockchip/make_fit_atf.sh \
        -t 0x08400000 > ${B}/u-boot.its
    ${B}/tools/mkimage -f ${B}/u-boot.its -E ${B}/u-boot.itb
}

# RK3588: the source-built tpl/u-boot-tpl.bin is a ~1KB version-string
# stub with no DDR init.  Rockchip's own make.sh takes the TPL stage
# (FlashData) from the rkbin DDR blob; use the same here or the bootrom
# jumps into a stub that only prints a banner and hangs before DDR
# training.  The RKSD idbloader has two segments (DDR blob + SPL).
do_compile:append:rk3588() {
    ${B}/tools/mkimage -n rk3588 -T rksd \
        -d ${DEPLOY_DIR_IMAGE}/ddr-rk3588.bin:${B}/spl/u-boot-spl.bin \
        ${B}/idbloader.img
}
# Rebuild the maskrom usbplug (CODE472) in a separate output directory:
# rockchip-usbplug_defconfig + configs/rk3576-usbplug.config, then the
# board fragment (UFS off, ZBIT NOR on) appended so its values win the
# olddefconfig pass.  ARCH=arm covers armv8 in this U-Boot generation.
# The -Werror strip mirrors the Seeed Armbian build: the plug sources
# predate the Wrynose host GCC.  sed is naturally idempotent, and the
# relaxation also applies to this tree's main build (harmless).
do_compile:append:rk3576() {
    USBPLUG_BUILD=${WORKDIR}/usbplug-build
    mkdir -p ${USBPLUG_BUILD}
    oe_runmake -C ${S} O=${USBPLUG_BUILD} ARCH=arm rockchip-usbplug_defconfig
    cat ${S}/configs/rk3576-usbplug.config \
        ${UNPACKDIR}/rk3576-usbplug-board.config >> ${USBPLUG_BUILD}/.config
    oe_runmake -C ${S} O=${USBPLUG_BUILD} ARCH=arm olddefconfig
    # Strip the standalone -Werror (the plug sources predate the Wrynose
    # host GCC; same relaxation Seeed's build applies).  The patterns anchor
    # on end-of-line/trailing-space so -Werror=date-time survives intact.
    sed -i -e 's/[[:space:]]*-Werror[[:space:]]*$//' \
        -e 's/[[:space:]]*-Werror[[:space:]]/ /g' \
        ${S}/Makefile ${S}/scripts/Makefile.build
    oe_runmake -C ${S} O=${USBPLUG_BUILD} ARCH=arm
    [ -f "${RK_USBPLUG}" ] || bbfatal "usbplug build did not produce ${RK_USBPLUG}"
}

# Build the Rockchip Maskrom download loader from the same SDK blobs and
# SPL used above.  This is deliberately separate from idbloader.img:
# rkdeveloptool db consumes the boot_merger format, while SD/eMMC/SPI
# boot consumes the RKSD idbloader format.  All placeholder substitution
# happens in the single sed pass (common DDR/USBPLUG/SPL plus the SoC
# boost entry) BEFORE boot_merger consumes the ini.
build_maskrom_loader() {
    install -Dm0644 ${UNPACKDIR}/${RK_SOC_FAMILY}-maskrom.ini ${B}/${RK_SOC_FAMILY}-maskrom.ini
    sed -i \
        -e "s|__DDR_PATH__|${DEPLOY_DIR_IMAGE}/ddr-${RK_SOC_FAMILY}.bin|g" \
        -e "s|__USBPLUG_PATH__|${RK_USBPLUG}|g" \
        -e "s|__SPL_PATH__|${B}/spl/u-boot-spl.bin|g" \
        ${B}/${RK_SOC_FAMILY}-maskrom.ini
    # The RK3576 maskrom .ini carries a fourth placeholder for the boost
    # segment (SRAM) of the three-segment NEWIDB loader; the RK3588
    # template has no such entry, so the substitution is a no-op there.
    if grep -q __BOOST_PATH__ ${B}/${RK_SOC_FAMILY}-maskrom.ini; then
        sed -i "s|__BOOST_PATH__|${DEPLOY_DIR_IMAGE}/boost-${RK_SOC_FAMILY}.bin|g" \
            ${B}/${RK_SOC_FAMILY}-maskrom.ini
    fi
    if [ ! -x "${RK_BOOT_MERGER}" ]; then
        bbfatal "Missing ${RK_SOC_FAMILY} boot_merger: ${RK_BOOT_MERGER}"
    fi
    (cd ${B} && "${RK_BOOT_MERGER}" ${B}/${RK_SOC_FAMILY}-maskrom.ini)
    install -Dm0644 ${B}/${RK_SOC_FAMILY}_spl_loader.bin ${B}/spl_loader_maskrom.bin
}
do_compile:append:rk3588() {
    build_maskrom_loader
}
do_compile:append:rk3576() {
    build_maskrom_loader
    # Storage-layout idblock from the same boot_merger run doubles as the
    # idbloader that gets written at sector 64 (see the function header).
    install -Dm0644 ${B}/rk3576_idblock.img ${B}/idbloader.img
}
# Assemble the SPI-NOR loader image using the same layout used by the
# Seeed Armbian integration (identical partition map for RK3576 and
# RK3588): GPT metadata plus idbloader at sector 64 and the U-Boot FIT
# at sector 16384. Keep this as an independent deployable artifact.
do_compile:append() {
    truncate -s 16M ${B}/rkspi_loader.img
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img mklabel gpt
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart idbloader 64 7167
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart vnvm 7168 7679
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart reserved_space 7680 8063
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart reserved1 8064 8127
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart uboot_env 8128 8191
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart reserved2 8192 16383
    ${STAGING_SBINDIR_NATIVE}/parted -s ${B}/rkspi_loader.img unit s mkpart uboot 16384 32734
    dd if=${B}/idbloader.img of=${B}/rkspi_loader.img bs=512 seek=64 conv=notrunc
    dd if=${B}/u-boot.itb of=${B}/rkspi_loader.img bs=512 seek=16384 conv=notrunc
}

DEPENDS:append = " parted-native"
do_deploy:append() {
    install -m0644 ${B}/${RK_SOC_FAMILY}_spl_loader.bin \
        ${DEPLOY_DIR_IMAGE}/${RK_SOC_FAMILY}_spl_loader.bin
    install -m0644 ${B}/spl_loader_maskrom.bin \
        ${DEPLOY_DIR_IMAGE}/spl_loader_maskrom.bin
    install -m0644 ${B}/rkspi_loader.img \
        ${DEPLOY_DIR_IMAGE}/rkspi_loader.img
    # Deploy both raw Rockchip boot-chain components as unversioned artifacts.
    install -m0644 ${B}/idbloader.img \
        ${DEPLOY_DIR_IMAGE}/idbloader.img
    install -m0644 ${B}/u-boot.itb \
        ${DEPLOY_DIR_IMAGE}/u-boot.itb
}
