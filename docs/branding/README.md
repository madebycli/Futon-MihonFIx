# Clio App Icon | SVG + Android + Google Play

This export reconstructs the approved dark-background temple/book design as **editable vector paths** from a raster illustration. **It is not the original design's native SVG source.** The traced silhouettes and reconstructed color gradients are approximations of the approved PNG; the original raster is included for visual comparison.

## Vector sources
- `vector/clio_icon.svg`: final artwork and full dark background, 1024×1024 coordinate system
- `vector/clio_foreground.svg`: transparent foreground layer; temple and book
- `vector/clio_monochrome.svg`: white silhouette for themed icons
- `vector/clio_background.svg`: dark backdrop

The SVG uses separate `<path>` objects with stable IDs for the roof, beam, three columns and three book shapes. All colors are editable `<linearGradient>` fills.

## PNG exports
- `png/clio_icon_{48,72,96,144,192,512,1024}.png`: square icon at common sizes
- `png/clio_google_play_512.png`: 512×512 Google Play asset
- `png/clio_foreground_1024.png`, `png/clio_monochrome_1024.png`
- `png/clio_original_approved_raster_1024.png`: original image for reference

## Android adaptive icons
The `android/res/` directory contains legacy density PNGs and an adaptive icon setup with an opaque dark background, a transparent foreground and a monochrome themed icon layer. Copy its contents into `app/src/main/res/` and set:

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

## Quality notes
- Checked that all SVGs are valid XML and renderable as PNG.
- Geometry is within the central ~60% of the square image (adaptive-icon safe-area approximation).
- The colored SVG uses a subtle gradient but maintains discrete editable silhouettes.
- Validate device-specific launcher masks and small-size appearance in the actual app; this pack is not a substitute for an on-device test.

## In this repository
- Android resources: `app/src/main/res/` (adaptive icon `mipmap-anydpi-v26/ic_launcher*.xml`, layers in
  `drawable-nodpi/`, legacy PNGs in `mipmap-*/`). Debug and nightly builds only override the background colour
  (`clio_icon_background`) so they can be told apart from release builds.
- Store icon: `metadata/en-US/images/icon.png`.
- `clio_icon_512.png` is used in the top-level `README.md`.
