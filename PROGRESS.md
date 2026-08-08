# Progress

Status key: ☐ not started · ◐ in progress · ☑ done and verified · ✗ blocked

| # | Milestone | Status | Notes |
|---|---|---|---|
| M0 | Skeleton — scaffold, Hilt, Compose, M3, nav, 4 themes, empty Library | ☑ | 2026-08-01 · verified on emulator |
| M1 | Library — SAF import, hashing, covers, Room v1, grid, sort/search | ☑ | 2026-08-01 · EPUB only; PDF import lands with its reader in M8 |
| M2 | Reader core — Readium, pagination, TOC, progress, typography | ☑ | 2026-08-01 · position restore is position-granular, see below |
| M3 | Gestures — full §2.3 layer, edge drags, zone-preview settings | ☑ | 2026-08-04 · two-finger toggle off by default |
| M4 | Highlighting — selection toolbar, locator anchoring, notes, tags | ☑ | 2026-08-08 · verified 80% → 300% font across a process kill |
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

Screenshots: `docs/screenshots/m0/`, captured by `tools/screenshots_m0.py` — all five theme
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

## M2 verification — 2026-08-01

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 33 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 15 tests, 0 failures |
| `./gradlew lint` | 0 errors, 3 warnings (`NewerVersionAvailable`, deliberate pins) |

Done-when, checked by hand on the emulator against a real EPUB:
- Read several pages, `adb shell am kill`, relaunched, reopened the book → **same page**,
  identical locator (`OPS/section-0003.html`, position 4).
- Raised the font size well past double → still the same resource and position, progress
  unchanged.

**Honest limit on position restore.** Readium anchors to its position list, roughly one
position per ~1024 characters. After a large font-size change the reader lands on the same
*position*, not the same line — visible text can shift by a paragraph or two within the
chapter. Fine for reading; M4's highlights use the finer text-quote selectors, where
exactness actually matters.

## M3 verification — 2026-08-04

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 54 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 21 tests, 0 failures |
| `./gradlew lint` | 0 errors, 3 warnings (deliberate version pins) |

On-device (emulator), against The Martian:
- Full-height vertical drag in the left strip: locator byte-identical before/after —
  **the page did not turn** — brightness indicator visible mid-drag, value persisted on
  finger-up.
- Right-strip drag up: font size 100% → 210% (12 steps × 10% over ~4.7 steps of travel
  minus the slop re-anchor), page position kept.
- Tap zones: right tap advanced (progression 0 → 0.4995), left tap returned to 0,
  centre tap toggled chrome.
- Settings: live zone diagram re-lays-out as the sliders move; tapping a zone highlights
  it and explains it.

Honest limits:
- **Two-finger theme toggle is OFF by default** (collides with pinch-zoom / TalkBack); it
  is covered by an instrumented MotionEvent test, not by a by-hand emulator check.
- **RTL inversion is unit-tested only** — no RTL EPUB on hand for an end-to-end check.
- Window brightness doesn't show in screencaps (framebuffer is pre-backlight); evidence is
  the persisted values and the locator staying put. The sub-minimum black overlay does
  composite into screenshots.
- Haptic ticks per font step: code is there; an emulator has no vibration motor to feel.

## M4 verification — 2026-08-08

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 67 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 23 tests, 0 failures |
| `./gradlew lint` | 0 errors |

**The done-when, end to end on the emulator against The Martian:**
1. Font shrunk to 80% via the right-edge drag; long-pressed "who" in
   *who calls me "Dude."*; tapped the yellow swatch.
2. Row written with the full three-part anchor — href
   `OEBPS/Text/dedicatoria.xhtml`, progression, and the quote selector
   (prefix `…me "Pickle," and Dad,`, exact `who`, suffix ` calls me "Dude`).
3. Font raised to **300%** by edge-drag, then `adb shell am kill`, then relaunched
   and reopened.
4. The highlight renders on **exactly the right word** — and on the correct "who",
   not the earlier *who calls me "Pickle,"* three lines above. Confirmed by pixel-scanning
   the framebuffer for the tint, not by eyeballing.
5. Tapping the rendered highlight opened the edit sheet; a note and two comma-separated
   tags saved and were read back from the database.

Screenshots in `docs/screenshots/m4/`.

**A wrong turn worth recording.** Mid-verification I scanned a dozen pages, found no
highlight tint anywhere, and concluded decorations were broken. They were not — the saved
reading position had wandered during testing and the highlight was simply on none of the
pages I looked at. Instrumenting the decoration path (`supportsHighlight=true`,
`applying 1 decorations`) disproved the theory in one build. The fix was to force the
book's saved locator to the highlight's own locator, which made the check deterministic.
Had I "fixed" the non-bug first, I would have broken working code.

## Open questions for the owner
- **Disk is at 95% (~24GB free)**, down from 39GB at M0. The Android toolchain accounts for
  ~11.6GB (SDK 6.4G, Gradle caches 3.3G, AVD 1.9G); the rest is build output and the
  emulator's growing disk image. The emulator wedged twice during M1 under this pressure —
  its package-manager service died once, and `uiautomator dump` hung once. Worth freeing
  space before M2.
- No physical device is attached to this Mac, so every "on device" check so far is really
  "on emulator". Worth plugging the phone in before M2, where reading feel starts to matter.
