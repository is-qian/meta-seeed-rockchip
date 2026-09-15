# Common settings shared by the Seeed reComputer Rockchip machines.
# Include order: the machine conf requires its SoC include (rk3576.inc /
# rk3588.inc, which set RK_SOC_FAMILY and the SoC tune) and then this file,
# so the SoC family override below is already resolvable here.

# Rebuild the override chain into canonical most-specific-last order:
#   <tunes>:rockchip:<soc>:<machine>
# The include chain up to here (bitbake.conf's "${MACHINE}" default, the
# armv8 tune prepends and rockchip-common.inc's ":rockchip" append) leaves
# MACHINEOVERRIDES as "tunes:${MACHINE}:rockchip", i.e. the vendor's
# :rockchip scope sits AFTER the board name.  bitbake resolves each
# override assignment at its FIRST occurrence in the chain, so merely
# re-appending the machine name later is a no-op (the earlier occurrence
# already consumed the assignment) and any ":rockchip"-scoped assignment
# would silently win over board- and SoC-scoped ones.  Deduplicate and
# move the SoC and machine names to the end, exactly once each.
MACHINEOVERRIDES_SNAPSHOT := "${MACHINEOVERRIDES}"
MACHINEOVERRIDES = "${@':'.join([p for p in d.getVar('MACHINEOVERRIDES_SNAPSHOT').split(':') if p and p != d.getVar('RK_SOC_FAMILY') and p != d.getVar('MACHINE')] + [d.getVar('RK_SOC_FAMILY'), d.getVar('MACHINE')])}"

# The board layer extends the vendor U-Boot recipe selected by the Rockchip
# base layer.
PREFERRED_PROVIDER_virtual/bootloader = "u-boot"

KERNEL_IMAGETYPE = "Image"

MACHINE_EXTRA_RRECOMMENDS += "kernel-modules"

# The Rockchip/Seeed kernels keep the debug UART behind the FIQ debugger,
# so the physical debug UART is exposed as ttyFIQ0 rather than the plain
# ttySx device.
SERIAL_CONSOLES = "1500000;ttyFIQ0"

# Hardware and multimedia packages shared by the Seeed BSP images.
IMAGE_INSTALL:append = " \
    kernel-devicetree \
    aic8800-firmware \
    aic8800-driver \
    rockchip-mpp \
    gstreamer1.0-rockchip \
    gstreamer1.0-plugins-base \
    gstreamer1.0-plugins-good-jpeg \
    gstreamer1.0-plugins-good-video4linux2 \
    v4l-utils \
    media-ctl \
"
