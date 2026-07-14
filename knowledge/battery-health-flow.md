# Battery Health Flow

## Import

The app accepts a ZIP through the Storage Access Framework or Android share sheet. It extracts entries into an isolated cache directory, recursively ranks text files by battery markers and size, then streams the selected file through the parser.

## Pixel Model Detection

Model evidence is ranked in this order:

1. Explicit product properties such as `ro.product.model`.
2. Device properties and build fingerprints containing a known Pixel codename.
3. The ZIP and selected text filenames.
4. A limited early-header fallback for recognizable model text.

Lower-ranked evidence cannot overwrite a model already identified by a stronger source.

Longer aliases are checked first so `Pixel 9 Pro XL` cannot be reduced to `Pixel 9`.

Pixel 9 is recognized by the display name `Pixel 9` and codename `tokay`. Its Google-listed typical design capacity is 4,700 mAh.

## Percentage Calculation

For a known model with estimated capacity:

`health percent = estimated capacity / model design capacity * 100`

Android's battery ASOC value is used only when estimated capacity or design capacity is unavailable. Inputs outside realistic battery ranges are ignored rather than shown as credible results.

## Privacy

All extraction and parsing happens on-device. The manifest must not request internet permission. Complete bugreports must never be added to the repository.
