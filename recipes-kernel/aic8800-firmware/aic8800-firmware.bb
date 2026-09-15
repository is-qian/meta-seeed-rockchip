SUMMARY = "Firmware for the AIC8800D80 SDIO WiFi/BT module (radxa-pkg pairing)"
DESCRIPTION = "Firmware set for the AIC8800D80 SDIO wireless module, taken \
from the same radxa-pkg/aic8800 revision as the out-of-tree driver recipe \
(driver and firmware are released as a validated pair; do not mix \
generations).  The per-chip firmware-path fix in the driver recipe expects \
the files under /lib/firmware/aic8800_fw/SDIO/aic8800D80/."
LICENSE = "CLOSED"

SRC_URI = "git://github.com/radxa-pkg/aic8800.git;protocol=https;nobranch=1;subpath=src/SDIO/driver_fw/fw"
SRCREV = "516e3b087763d80c44f5e3b6d2dd63e0d925c91d"

S = "${UNPACKDIR}/fw"


do_install() {
    install -d ${D}${nonarch_base_libdir}/firmware/aic8800_fw/SDIO/aic8800D80
    install -m 0644 ${S}/aic8800D80/* \
        ${D}${nonarch_base_libdir}/firmware/aic8800_fw/SDIO/aic8800D80/
}

FILES:${PN} = "${nonarch_base_libdir}/firmware/aic8800_fw/*"

INSANE_SKIP:${PN} += "arch"
