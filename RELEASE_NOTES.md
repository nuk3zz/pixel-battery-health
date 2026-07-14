# Pixel Battery Health v1.1.0

Pixel 9 accuracy and reliability update.

## Improved

- Prioritizes authoritative product properties, codenames, and build fingerprints for model detection
- Explicit Pixel 9 and `tokay` detection using a 4,700 mAh typical design capacity
- Calculates percentage from estimated capacity before considering Android ASOC fallback data
- Supports formatted capacity values such as `4,230.0 mAh`
- Scans complete candidate text files for battery markers instead of only the first 2,000 lines
- Avoids false cycle counts from unrelated battery-usage fields
- Restricts generic temperature, voltage, and health values to the Battery Service section
- Rejects impossible capacity and ASOC values
