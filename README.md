# meta-seeed-rockchip

`meta-seeed-rockchip` is the hardware BSP layer for Seeed Studio reComputer
Rockchip boards. It contains board machine configuration, device trees,
boot firmware, U-Boot, kernel, Wi-Fi/BT support, USB gadget support, and
Rockchip camera runtime recipes.

## Supported devices

- `recomputer-rk3576-devkit`: Seeed Studio reComputer RK3576 DevKit
- `recomputer-rk3588-devkit`: Seeed Studio reComputer RK3588 DevKit

## Dependencies

This layer currently targets the Yocto Project Wrynose series. The dependency
paths are kept explicit so a future Yocto series can be added without changing
the BSP recipe layout. It requires these layers:

- `meta` / OpenEmbedded-Core
- `meta-openembedded/meta-oe`
- the Rockchip base layer that provides the `rockchip` collection and its SoC
  machine includes

## Source pins

The board recipes intentionally pin the vendor source revisions used by the
supported machines:

- Linux: `armbian/linux-rockchip`, branch `rk-6.1-rkr7.2`, revision
  `5ef479b1070b9d74dcefce859826176ce0eb5fe1`
- U-Boot: `radxa/u-boot`, branch `next-dev-v2024.10`, revision
  `39cd993e5d6296635438e84f4576b3a9bf76f86e`

## Integration

Add the layer to `BBLAYERS` and select one of the supported machine names.
The layer also provides `seeed-rockchip-image`, a small standalone image
based on `core-image-minimal`. It is intended for hardware bring-up and CI;
product-specific partitioning, OTA, and container integration remain the
responsibility of the consuming distribution layer.

The layer exports `SEEED_ROCKCHIP_LAYERDIR` from `conf/layer.conf` for
optional overlay layers. Consumers should use that variable rather than
assume a sibling checkout layout.

## Standalone Yocto build

The standalone build uses OpenEmbedded-Core, BitBake, `meta-yocto`,
`meta-openembedded/meta-oe`, and `meta-rockchip` from their Wrynose branches.
CI additionally pins those dependencies to known-good commits so upstream
branch movement does not make a build non-reproducible.

Place those repositories beside this layer, then run:

```sh
POKY_DIR=$PWD/poky ./scripts/build-yocto-image.sh \
    recomputer-rk3588-devkit
```

The `POKY_DIR` name is retained for compatibility with the standard Yocto
environment script; it points to the OpenEmbedded-Core checkout. The
BitBake checkout is expected at `./bitbake`, and
`meta-yocto` checkout is expected at `./meta-yocto`, or can be overridden with
`BITBAKE_DIR` and `META_YOCTO_DIR`.

Use `recomputer-rk3576-devkit` for the RK3576 board. The deploy directory is
`build-<machine>/tmp/deploy/images/<machine>/`.

## Continuous integration

GitHub Actions workflow `.github/workflows/yocto.yml` is manually triggered.
It lets you select the board set (`all`, RK3576, or RK3588) and exposes a
Yocto-version selector that defaults to `wrynose`. The current CI supports
Wrynose; future series can be added by extending the branch mapping.
