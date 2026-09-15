SUMMARY = "Minimal Yocto image for Seeed reComputer Rockchip boards"
DESCRIPTION = "A small standalone Yocto image containing the core boot and diagnostic packages for Seeed reComputer Rockchip boards."
LICENSE = "MIT"

inherit core-image

IMAGE_FEATURES += "ssh-server-dropbear"

IMAGE_INSTALL:append = " \
    bash \
    e2fsprogs-resize2fs \
    ethtool \
    i2c-tools \
    iproute2 \
    iputils \
    mmc-utils \
    pciutils \
    usbutils \
    util-linux \
    v4l-utils \
"

IMAGE_LINGUAS = ""

# Keep the image useful for first boot while avoiding a product-specific
# partitioning or OTA layout. Consumers can add their own image features and
# packages through local.conf or a product layer.
IMAGE_ROOTFS_SIZE ?= "8192"
