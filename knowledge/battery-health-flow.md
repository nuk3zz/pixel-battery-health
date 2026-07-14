# Battery Health Flow

## Import

The app accepts a ZIP through the Storage Access Framework or Android share sheet. It extracts entries into an isolated cache directory, recursively ranks text files by battery markers and size, then streams the selected file through the parser.

Import reports separate preparation, optional backup, extraction, text discovery, and parsing stages. Byte progress is shown when Android supplies the source size. Each long-running read checks cancellation, and the complete operation times out after three minutes with the active stage included in the error. Stale extraction directories are removed before import, and newly extracted data is deleted immediately after parsing.

## Pixel Model Detection

Model evidence is ranked in this order:

1. Explicit product properties such as `ro.product.model`.
2. Device properties and build fingerprints containing a known Pixel codename.
3. The ZIP and selected text filenames.
4. A limited early-header fallback for recognizable model text.

Lower-ranked evidence cannot overwrite a model already identified by a stronger source.

Longer aliases are checked first so a Pro, XL, or Fold variant cannot be reduced to its shorter base-model name.

The built-in model catalog covers the original Pixel through the Pixel 10 series, including supported XL, `a`, Pro, and Fold variants. Each entry maps model names and device codenames to the model's typical design capacity.

## Percentage Calculation

For a known model with estimated capacity:

`health percent = estimated capacity / model design capacity * 100`

Android's battery ASOC value is used only when estimated capacity or design capacity is unavailable. Inputs outside realistic battery ranges are ignored rather than shown as credible results. The user-facing percentage is capped at 100% because an individual battery's measured or learned capacity can be higher than the manufacturer's typical rating; the original measured mAh value remains visible.

## Privacy

All extraction and parsing happens on-device. The manifest must not request internet permission. Complete bugreports must never be added to the repository.
