#!/bin/sh
# Persistent NCM USB network gadget for the reComputer DevKit Type-C
# OTG port (dwc3 dr_mode=otg).  Pattern follows the Seeed Armbian
# usb-gadget-network BSP package: compose the gadget through configfs and
# bind it to the UDC whenever the controller shows up in peripheral mode.
# Safe to run repeatedly (boot coldplug and every replug).

CONFIGFS=/sys/kernel/config/usb_gadget

[ -d "$CONFIGFS" ] || exit 0

UDC=$(ls /sys/class/udc 2>/dev/null | head -1)
if [ -z "$UDC" ]; then
    echo "usb-gadget: no UDC (cable unplugged / host mode), skipping"
    exit 0
fi

mkdir -p "$CONFIGFS/g1"
cd "$CONFIGFS/g1" || exit 1

# Linux Foundation NCM (Ethernet) gadget, same IDs Armbian uses so hosts
# pick a generic driver out of the box.
echo 0x1d6b > idVendor
echo 0x0103 > idProduct
echo 0x0200 > bcdUSB
echo 0x0100 > bcdDevice

mkdir -p strings/0x409
echo "Seeed" > strings/0x409/manufacturer
# The product string is shared by all reComputer machines (the script
# ships in one package for all of them); hosts only show it as a label.
echo "Seeed reComputer DevKit" > strings/0x409/product
echo "0123456789" > strings/0x409/serialnumber

mkdir -p functions/ncm.usb0
# Fixed MACs: without them the NCM function draws a random address on every
# composition, so the host interface (enx<MAC>) and its static peer address
# (10.55.0.1) are lost on each replug/reboot.  Locally administered OUI.
# The writes fail with EBUSY when the function is already bound (rerun);
# the addresses only take effect on a fresh composition anyway.
echo "02:ee:55:0d:55:02" > functions/ncm.usb0/dev_addr 2>/dev/null || true
echo "02:ee:55:0d:55:01" > functions/ncm.usb0/host_addr 2>/dev/null || true
mkdir -p configs/c.1/strings/0x409
echo 250 > configs/c.1/MaxPower
echo "NCM" > configs/c.1/strings/0x409/configuration

[ -e configs/c.1/ncm.usb0 ] || ln -s functions/ncm.usb0 configs/c.1/ncm.usb0

# Writing UDC activates the gadget; only (re)bind when it changed, e.g.
# after a replug the controller reappears with the gadget unbound.
[ "$(cat UDC 2>/dev/null)" = "$UDC" ] || echo "$UDC" > UDC

# Board-side address of the point-to-point link; the host peer is expected
# at 10.55.0.1 (set once on the host, NetworkManager remembers it).
ip link set usb0 up
ip addr add 10.55.0.2/24 dev usb0 2>/dev/null

# Tiny DHCP server on the link so a plugged host configures itself
# (same idea as Armbian's unudhcpd): hosts get 10.55.0.10-20 and can
# reach the board at 10.55.0.2 with zero manual configuration.
mkdir -p /run/usb-gadget
cat > /run/usb-gadget/udhcpd.conf <<EOF
interface usb0
start 10.55.0.10
end 10.55.0.20
opt subnet 255.255.255.0
opt router 10.55.0.2
opt dns 10.55.0.2
option lease 3600
EOF
# udhcpd must NOT run as a child of this oneshot unit: when the unit goes
# inactive systemd kills the whole cgroup, silently reaping the daemon
# (that was the "udhcpd works until replug" bug).  Keep it in its own
# service; restart picks up the fresh config on every (re)composition.
# --no-block is mandatory: this script runs INSIDE usb-gadget-network.service
# and the udhcpd unit orders itself After this unit - a blocking restart
# would wait for this unit to finish, which waits on this script: deadlock
# (boot would hang with usb-gadget-network stuck in "activating").
systemctl restart usb-gadget-udhcpd.service --no-block || true

echo "usb-gadget: NCM gadget bound to $UDC (usb0 10.55.0.2/24, DHCP pool 10.55.0.10-20)"
