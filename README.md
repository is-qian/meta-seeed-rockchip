# meta-seeed-rockchip

`meta-seeed-rockchip` is the hardware BSP layer for Seeed Studio reComputer
Rockchip boards. It contains board machine configuration, device trees,
boot firmware, U-Boot, kernel, Wi-Fi/BT support, USB gadget support, and
Rockchip camera runtime recipes. It deliberately contains no Balena runtime,
image layout, OTA, container, or boot-environment integration.

## Supported devices

- `recomputer-rk3576-devkit`: Seeed Studio reComputer RK3576 DevKit
- `recomputer-rk3588-devkit`: Seeed Studio reComputer RK3588 DevKit

## Dependencies

This layer targets Yocto Project Wrynose and requires these layers:

- `meta` / OpenEmbedded-Core
- `meta-openembedded/meta-oe`
- the Rockchip base layer that provides the `rockchip` collection and its SoC
  machine includes

For BalenaOS builds, add `meta-balena-rockchip` as a separate overlay layer.
It depends on this layer's `seeed-rockchip` collection and adds all Balena
specific recipes and configuration.

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
it does not define a Balena partition layout, OTA scheme, or container
runtime.

The layer exports `SEEED_ROCKCHIP_LAYERDIR` from `conf/layer.conf` for
optional overlay layers. Consumers should use that variable rather than
assume a sibling checkout layout.

## Standalone Yocto build

The standalone build uses Poky, `meta-openembedded/meta-oe`, and
`meta-rockchip` from the `wrynose` branches. Place those repositories beside
this layer, then run:

```sh
POKY_DIR=$PWD/poky ./scripts/build-yocto-image.sh \
    recomputer-rk3588-devkit
```

Use `recomputer-rk3576-devkit` for the RK3576 board. The deploy directory is
`build-<machine>/tmp/deploy/images/<machine>/`.

## Continuous integration

GitHub Actions workflow `.github/workflows/yocto.yml` parses and builds the
standalone image for both supported machines on pushes and pull requests.
