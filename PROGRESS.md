# Progress

Status key: ☐ not started · ◐ in progress · ☑ done and verified · ✗ blocked

| # | Milestone | Status | Notes |
|---|---|---|---|
| M0 | Skeleton — scaffold, Hilt, Compose, M3, nav, 4 themes, empty Library | ☑ | 2026-08-01 · verified on emulator |
| M1 | Library — SAF import, hashing, covers, Room v1, grid, sort/search | ☑ | 2026-08-01 · EPUB only; PDF import lands with its reader in M8 |
| M2 | Reader core — Readium, pagination, TOC, progress, typography | ☐ | |
| M3 | Gestures — full §2.3 layer, edge drags, zone-preview settings | ☐ | riskiest milestone |
| M4 | Highlighting — selection toolbar, locator anchoring, notes, tags | ☐ | |
| M5 | Dictionary — OEWN DB, Morphy lemmatizer, sheet, lookup log | ☐ | |
| M6 | Notes database — cross-book list, FTS, export, backup/restore | ☐ | |
| M7 | Review mode — flashcard queue, spaced intervals | ☐ | |
| M8 | PDF — render, page turn, rect highlights, text lookup | ☐ | |
| M9 | Ship — signed release APK, R8 verified, README | ☐ | |

## Environment (this machine, 2026-08-01)

Nothing Android-related was installed before this project; all of it was added from scratch.

| Item | Status |
|---|---|
| JDK 21 — `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` | ☑ 21.0.12 |
| Android SDK — `/opt/homebrew/share/android-commandlinetools` | ☑ |
| Platforms 36 + 37.1, build-tools 37.0.0, platform-tools | ☑ |
| Gradle 9.6.1 (wrapper committed; brew gradle only bootstrapped it) | ☑ |
| Emulator AVD `lumen_test` (Pixel 7, API 36, arm64) | ☑ headless |
| Physical device attached to this Mac | ✗ not attached — everything below ran on the emulator |

Building needs `JAVA_HOME` set, since the JDK is keg-only:

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/opt/homebrew/share/android-commandlinetools/platform-tools:$PATH"
```

## M0 verification — 2026-08-01

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 10 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 2 tests, 0 failures |
| `./gradlew lint` | 0 errors, 3 warnings (all `NewerVersionAvailable`, deliberate pins) |

Screenshots: `docs/screenshots/m0/`, captured by `tools/screenshots.py` — all five theme
options on both screens, plus AMOLED surviving `am kill` and "follow system" tracking a
system dark-mode switch.

Measured cold start (emulator, software GPU): `am start -W` TotalTime ≈ 1.4s. That is the
emulator's number, not a phone's, and §5's <1s bar is unverified until it runs on the real
device.

## M1 verification — 2026-08-01

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL (1h 19m cold, 1m 13s incremental) |
| `./gradlew test` | 28 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 15 tests, 0 failures |
| `./gradlew lint` | 0 errors, 3 warnings (`NewerVersionAvailable`, deliberate pins) |

Three real EPUBs imported through the actual system file picker; correct covers, titles and
authors on all three. Screenshots in `docs/screenshots/m1/`.

**Fixed during M1, a defect introduced in M0:** the colour schemes only specified
`background`/`surface`, so Material filled the whole `surfaceContainer*` family from its
baseline purple — menus and dialogs rendered lavender on warm paper. All four palettes now
specify every M3 role.

**Build times are the main friction.** A cold full compile is over an hour on this machine;
incremental is ~1 minute. Plan milestone work around not needing many cold builds.

## Open questions for the owner
- Disk is at 91% (39GB free). The SDK + emulator image took ~8GB of that.
- No physical device is attached to this Mac, so every "on device" check so far is really
  "on emulator". Worth plugging the phone in before M2, where reading feel starts to matter.
