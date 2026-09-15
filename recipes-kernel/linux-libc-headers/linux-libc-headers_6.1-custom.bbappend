# JeffyCN/meta-rockchip still names the historical date-based branch
# kernel-6.1-2024_01_02 in SRC_URI, but that branch has been deleted from
# the JeffyCN/mirrors mirror. Fetch by the pinned SRCREV instead.
SRC_URI = "git://github.com/JeffyCN/mirrors.git;protocol=https;nobranch=1"
