# Pixel Battery Health v1.2.0

Import progress and measured-capacity display update.

## Improved

- Shows separate progress stages for saving, extraction, text discovery, and parsing
- Shows real percentage progress when the source size is available
- Adds a Cancel Import action and a three-minute processing timeout
- Reports the exact stage where an import timed out or failed
- Keeps long-running parsing and scanning cancellation-aware
- Caps displayed battery health at 100% when learned capacity exceeds the typical design rating
- Explains above-typical measured capacity directly on the results screen
- Adds extraction size and entry-count safeguards for malformed ZIP files
- Removes stale extraction folders and deletes temporary report data after every import
