SUMMARY = "Persistent USB NCM network gadget over the Type-C OTG port"
DESCRIPTION = "Composes an NCM USB gadget through configfs and binds it to \
the dwc3 UDC whenever the controller is in peripheral mode (boot and every \
replug, via a udev rule).  Gives a point-to-point network link to a host \
computer for scp/rsync access on 10.55.0.0/24.  Pattern follows the Seeed \
Armbian usb-gadget-network BSP package."
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

SRC_URI = " \
    file://usb-gadget-network.service \
    file://usb-gadget-udhcpd.service \
    file://setup-usb-gadget-network.sh \
    file://99-usb-gadget-network.rules \
"

S = "${UNPACKDIR}"

inherit systemd

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${S}/setup-usb-gadget-network.sh ${D}${bindir}/

    install -d ${D}${systemd_unitdir}/system
    install -m 0644 ${S}/usb-gadget-network.service ${D}${systemd_unitdir}/system/
    install -m 0644 ${S}/usb-gadget-udhcpd.service ${D}${systemd_unitdir}/system/

    install -d ${D}${nonarch_base_libdir}/udev/rules.d
    install -m 0644 ${S}/99-usb-gadget-network.rules ${D}${nonarch_base_libdir}/udev/rules.d/
}

SYSTEMD_SERVICE:${PN} = "usb-gadget-network.service usb-gadget-udhcpd.service"
SYSTEMD_AUTO_ENABLE = "enable"

FILES:${PN} = " \
    ${bindir}/setup-usb-gadget-network.sh \
    ${systemd_unitdir}/system/usb-gadget-network.service \
    ${systemd_unitdir}/system/usb-gadget-udhcpd.service \
    ${nonarch_base_libdir}/udev/rules.d/99-usb-gadget-network.rules \
"
