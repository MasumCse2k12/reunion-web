# Play Store Assets

Graphics for the Play Console **store listing**. Nothing here is compiled into
the app — these are uploaded by hand in the Console.

## `play-store-icon-512.png`

| | |
|---|---|
| Size | 512 × 512 |
| Format | 32-bit PNG (RGBA, fully opaque) |
| File size | 279 KB (Play's limit is 1 MB) |
| Source | `web/sammilani_logo.jpeg` |

Generated from the school seal: near-white pixels normalised to remove the
JPEG haze, cropped to the artwork, oversampled 4× with sharpening, then
resized down. Centred to the pixel on the artwork rather than on the source
frame — the seal sits off-centre inside its own JPEG.

Left deliberately square and opaque with no rounded corners. Play applies its
own corner masking, so baking in rounding produces a double-rounded icon.

### Known limitation: this is an upscale

The source is **271 × 282**, below the 512 target, so this is a ~1.9×
enlargement and is softer than a native-resolution asset would be. It passes
Play's requirements and reads correctly at listing size, but the fine Bengali
lettering in the outer ring is the first thing to show it.

If anyone can get the original artwork from the school — a vector (SVG / AI /
PDF / EPS) or any raster at 512 px or larger — regenerate from that instead.
The script lives in this repo's history; it is a dozen lines of Pillow.

## `play-feature-graphic-1024x500.png`

| | |
|---|---|
| Size | 1024 × 500 |
| Format | 24-bit PNG, no alpha |
| File size | 173 KB (Play's limit is 15 MB) |

Deep-green gradient with the gold reunion accent, taken from
`app/src/main/res/values/colors.xml` so the listing matches the app rather
than the crimson of the seal. The seal sits in a white badge with a gold rim,
which is what keeps crimson linework readable against green.

All artwork stays within **80 px of every edge**. Play crops this image to
different aspect ratios across surfaces, and anything near the border is the
first thing lost.

Two rendering details that are easy to get wrong if this is ever regenerated:

- Bengali requires `ImageFont.Layout.RAQM`. Without libraqm the conjuncts
  (`ন্ত`, `ছা`, `ব্যা`) decompose into separate glyphs.
- `NotoSansBengali` contains **no Latin glyphs**. Mixed lines such as
  "একসাথে আবার · Together Again" must be drawn as separate runs per script, or
  the Latin half renders as tofu boxes.

### If you add a promo video

Play overlays a play button over the **centre** of the feature graphic when a
video is attached. Centre here is roughly where "GRAND REUNION" begins, so the
overlay would clip it. Shift the text column right, or drop the video.

## Not covered here

- **Screenshots** — at least 2 phone screenshots required.
- **Launcher icon** — the in-app icon under `app/src/main/res/mipmap-*/` is a
  separate asset and is currently an unrelated "A" lettermark, not this seal.
  See the note in the repo README before changing it: a detailed seal does not
  survive being scaled down to a 48 px launcher icon.
