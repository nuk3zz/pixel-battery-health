# Learnings

## 2026-07-14 - Pixel 9 Finalization

- The project already recognized Pixel 9 by `Pixel 9` and `tokay`, using the correct 4,700 mAh typical capacity.
- The existing percentage logic preferred `mSavedBatteryAsoc` over the requested estimated-capacity formula. This can produce a result that does not match the displayed capacities.
- Model detection needs to prioritize explicit product properties and build fingerprints because bugreports can mention other Pixel models inside unrelated logs.
- Model evidence needs confidence ranking; otherwise a later crash-log fingerprint can overwrite the actual `ro.product.model` value.
- `mSavedBatteryUsage` is not a reliable cycle-count source and must not populate the cycle count.
- Generic `temperature` and `voltage` labels occur in non-battery services, so those labels are parsed only inside Battery Service output.
- Candidate text markers can occur after line 2,000 in large reports; full streaming scans preserve low memory use while finding them reliably.
- Release APKs are not installable without a signing configuration; the locally signed debug APK is appropriate for direct GitHub sideload testing.
- v1.1.0 was installed and launched on the Android 16 Pixel 9 emulator. A synthetic `tokay` ZIP imported through the system document picker produced the expected 90% result from 4,230 / 4,700, along with the expected cycle, health, temperature, and voltage values.
- The installed package resolved Android `ACTION_SEND` for `application/zip`, and APK manifest inspection confirmed there is no internet permission.
