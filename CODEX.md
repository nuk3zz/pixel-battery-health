# Pixel Battery Health Project Instructions

## Knowledge Architecture

- Store unprocessed bugreport excerpts, external notes, and feature requests in `raw/`.
- Convert useful raw inputs into focused Markdown pages in `knowledge/`.
- Keep architecture, parsing rules, model data, and release procedures in separate knowledge pages.
- Link related pages with relative Markdown links and update them when behavior changes.
- Record each implementation or debugging session in `learnings.md`.

## Code Conventions

- Use Kotlin, Jetpack Compose, Material 3, MVVM, and coroutines.
- Keep parsing and ZIP handling independent from Compose UI.
- Parse bugreports as streams; do not load multi-megabyte reports entirely into memory in production paths.
- Prefer explicit model-property and codename evidence over incidental log text.
- Add focused unit tests for every new bugreport format or model alias.
- Keep the app fully offline and never add the Android internet permission.

## Naming

- Kotlin types use `UpperCamelCase`; functions and properties use `lowerCamelCase`.
- Test names describe behavior in plain language.
- Knowledge filenames use lowercase kebab-case.

## Raw Input Protocol

1. Preserve incoming source material unchanged in `raw/` when it is needed for future reference.
2. Do not commit real bugreports because they can contain sensitive user data. Store only minimal, anonymized excerpts.
3. Summarize durable behavior in `knowledge/` and cite the raw filename.
4. Add parser fixtures or unit tests using synthetic values.
5. Record successful and failed approaches in `learnings.md`.

## Verification

- Run `./gradlew testDebugUnitTest` after parser or model changes.
- Run `./gradlew assembleDebug` before publishing an APK.
- Verify the manifest still has no internet permission.
