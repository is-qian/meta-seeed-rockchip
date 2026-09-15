SUMMARY = "AIC8800 SDIO WiFi/BT out-of-tree kernel modules from the radxa-pkg source"
DESCRIPTION = "Same vendor driver family the reComputer boards ship with, \
packaged the way Seeed's Armbian integration consumes it: an out-of-tree \
module built against the running kernel (their DKMS deb, our module-class \
recipe), so the kernel tree itself stays free of the 83k-line vendor \
driver.  Driver and firmware must stay paired: both recipes pin the same \
radxa-pkg/aic8800 revision."
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://aic8800_bsp/Makefile;md5=c982e4f81b09d48bc3076fd373c57039"

inherit module

# Every platform branch in the vendor Makefile hardcodes a build machine
# path (or /lib/modules/$(shell uname -r)/build); command-line variables
# override all of them and point the nested "make -C $(KDIR)" at the
# cross kernel in the sysroot.
EXTRA_OEMAKE = "KDIR=${STAGING_KERNEL_DIR} ARCH=arm64 CROSS_COMPILE=${TARGET_PREFIX}"

# Pin main HEAD carrying the SDIO firmware-path fix series (2026-09-01);
# older release snapshots load SDIO firmware from the wrong directory.
# The two patches are that fix series, lifted from the repo's debian/
# packaging (paths rewritten for the subpath checkout).
SRC_URI = "git://github.com/radxa-pkg/aic8800.git;protocol=https;nobranch=1;subpath=src/SDIO/driver_fw/driver/aic8800 \
           file://0001-fix-sdio-firmware-path.patch \
           file://0002-fix-sdio-per-chip-firmware-path.patch \
           file://0003-quiet-default-loglevel.patch \
           "
SRCREV = "516e3b087763d80c44f5e3b6d2dd63e0d925c91d"

# The git fetcher unpacks the subpath content into ${UNPACKDIR}/<basename>
# ("aic8800" here); the recipe default S does not match that layout.
S = "${UNPACKDIR}/aic8800"


# Module names keep their upstream plain form so udev modalias autoload
# finds aic8800_fdrv/aic8800_bsp/aic8800_btlpm exactly like the previous
# in-tree modules.
RDEPENDS:${PN} += "aic8800-firmware"
RPROVIDES:${PN} += "kernel-module-aic8800-fdrv kernel-module-aic8800-bsp kernel-module-aic8800-btlpm"

COMPATIBLE_MACHINE = "^recomputer-"

# The vendor Makefile has no modules_install target (its install rule does a
# bare copy to /lib/modules/$(uname -r) and runs the host depmod), so install
# the three modules where kmod on the target expects them.
do_install() {
    install -d ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra
    install -m 0644 ${S}/aic8800_bsp/aic8800_bsp.ko \
        ${S}/aic8800_fdrv/aic8800_fdrv.ko \
        ${S}/aic8800_btlpm/aic8800_btlpm.ko \
        ${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/extra/
}
