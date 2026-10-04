# Progress

Status key: ☐ not started · ◐ in progress · ☑ done and verified · ✗ blocked

| # | Milestone | Status | Notes |
|---|---|---|---|
| M0 | Skeleton — scaffold, Hilt, Compose, M3, nav, 4 themes, empty Library | ☑ | 2026-08-01 · verified on emulator |
| M1 | Library — SAF import, hashing, covers, Room v1, grid, sort/search | ☑ | 2026-08-01 · EPUB only; PDF import lands with its reader in M8 |
| M2 | Reader core — Readium, pagination, TOC, progress, typography | ☑ | 2026-08-01 · position restore is position-granular, see below |
| M3 | Gestures — full §2.3 layer, edge drags, zone-preview settings | ☑ | 2026-08-04 · two-finger toggle off by default |
| M4 | Highlighting — selection toolbar, locator anchoring, notes, tags | ☑ | 2026-08-08 · verified 80% → 300% font across a process kill |
| M5 | Dictionary — WordNet DB, Morphy lemmatizer, sheet, lookup log | ☑ | 2026-08-08 · verified in airplane mode |
| M6 | Notes database — cross-book list, FTS, export, backup/restore | ☑ | 2026-08-08 · full wipe-and-restore verified on device |
| M7 | Review mode — flashcard queue, spaced intervals | ☑ | 2026-08-08 · ladder verified across a real day boundary |
| M8 | PDF — render, page turn, rect highlights, text lookup | ⏸ | parked on `m8-pdf` — compiles, never executed, no tests |
| M9 | Ship — signed release APK, R8 verified, README | ☑ | 2026-09-17 · EPUB-only; R8 build opened a book on the emulator |
| M10 | Reading comforts, folder scan, stats, auto-scroll → 1.1.0 | ☑ | 2026-09-18 · every item verified by measurement, not by eye |
| M10.1 | Two tabs; Home dashboard with heatmap, week/hour shape, pace, books, records → 1.2.0 | ☑ | 2026-09-18 · charts are Canvas; insights unit-tested incl. zone handling |
| M11 | Read-aloud (phone voices) + remembered-folder search → 1.3.0 | ☑ | 2026-10-04 · Piper deferred until the owner has heard the free voice |

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

## M5 verification — 2026-08-08

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 90 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 31 tests, 0 failures |
| `./gradlew lint` | 0 errors |

**The done-when, with the network genuinely off.** `adb shell cmd connectivity airplane-mode
enable`, confirmed by `ping 8.8.8.8` → *Network is unreachable*, then the instrumented
suite ran against the real bundled 20.5MB asset:

- `runningResolvesToRun` — "running" → its own adjective/noun senses **and** the verb "run"
- `miceResolvesToMouse` — "mice" → "mouse", rodent sense present
- `betterResolvesToGoodAndWell` — "better" → both "good" and "well"
- plus pronunciation present, punctuation trimmed, unknown words empty, and the
  asset-install path exercised from a clean slate.

These live in `WordNetDictionaryTest` rather than being a one-off demo, so the bar stays
enforced. There is no network code in the dictionary path at all and the app still declares
**zero permissions**, so a regression cannot hide behind a silent online fallback.

**Sizes:** dictionary asset 20.5MB → debug APK 39MB. *(Prediction corrected in M9: the
release APK is 12.4MB, not ~39MB — the SQLite asset compresses hard inside the APK zip. The
asset is present and intact in the shrunk build; that was checked, not assumed.)*

## M6 verification — 2026-08-08

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 105 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 36 tests, 0 failures |
| `./gradlew lint` | 0 errors, 4 warnings (deliberate pins) |

**The done-when, run end to end on the emulator — every step of it:**
1. Highlighted a phrase in *The Martian*; it appeared in the cross-book notes list with
   its book and chapter.
2. Searched from the notes screen: a non-matching query emptied the list
   ("Nothing matches those filters"), the matching one returned the highlight — so the
   FTS filter is demonstrably doing the work, not just rendering everything.
3. Tapped the entry → the reader opened **on the exact page**, highlight visible and
   tinted (confirmed by pixel-scanning the framebuffer).
4. Exported Markdown through the real SAF directory picker into a vault folder. The file
   on disk has valid YAML frontmatter (title, author, count, content hash), an `H1`, a
   `## Chapter 1` heading and the quote as a blockquote.
5. Backed up to `lumen-backup.zip` (10.5 KB), then **`adb shell pm clear`** — library
   confirmed empty — then restored from that zip.
6. Back afterwards: 3 books, the highlight with its quote and chapter, the **FTS index
   working** (`MATCH 'They*'` returns the row), and the persisted theme.

Screenshots in `docs/screenshots/m6/`.

**A defect found while verifying, introduced in M4.** Every highlight colour swatch
announced itself to screen readers as the literal text `Highlight ${color.displayName}` —
an escaping mistake in the patch that added the label meant the Kotlin emitted a dollar
sign instead of interpolating. Fixed; the swatches now read "Highlight Yellow" and the
rest. It was only visible because the accessibility tree is how these scripts drive the UI.

**Platform limit worth knowing:** Android refuses to grant directory access to the
`Download` root, so the Markdown export needs a *sub*folder (a vault directory, which is
the realistic case anyway). Nothing to fix — the picker simply disables "Use this folder"
there.

## M7 verification — 2026-08-08

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew test` | 120 tests, 0 failures |
| `./gradlew connectedAndroidTest` | 46 tests, 0 failures |
| `./gradlew lint` | 0 errors, 4 warnings (deliberate pins) |

**The done-when — "words I looked up yesterday appear in today's queue" — proved twice,
once in a test and once on the device.**

In `ReviewQueueTest` it is a real Room database with a lookup dated a day back, and the
clock passed in rather than waited for. Nine cases cover the whole ladder: highlights join
the queue too, unresolved lookups never do, seeding twice neither duplicates a card nor
resets one already answered, KNOWN pushes a card out of today and back in four days,
AGAIN returns it tomorrow, and a card that climbs all five rungs graduates and never
returns.

On the emulator, with rows seeded directly by sqlite so the UI was not driving itself:
1. Notes screen showed the **"2 due"** badge.
2. Review screen showed **"It was ______ than expected."** — the term blanked, "Card 1 of 2".
3. Reveal showed **"better"** and "Next in 1d if you know it".
4. Tapping Known wrote `better | interval_index 1 | KNOWN` and left `mice` still due,
   now rendered as **"The ____ were everywhere in the room."**

Screenshots in `docs/screenshots/m7/`.

**The schedule survived a real day boundary**, which no test can claim. Re-reading the
database a day after those taps, `better` was still correctly out of the queue with 53
hours left of its 3-day interval, and `mice` was still the only card due. The arithmetic
holds against the wall clock, not just against an injected one.

**On not building SM-2.** §2.6 said a simple 1/3/7/21/60 ladder was enough and not to build
SM-2 "unless it falls out for free". It does not: SM-2 needs a per-card ease factor and a
0–5 graded answer, and this app asks a binary known/again. So the ladder is exactly what
was asked for and nothing more — see `ReviewSchedule`, which is pure arithmetic on millis
with `now` always passed in.

**Migration.** Room schema v4 → v5 adds `review_items` with a `UNIQUE(source_type,
source_id)` index, which is what makes re-seeding idempotent. `MigrationTest` now runs the
**whole v1→v5 chain** and asserts a highlight written against the very first schema is
still intact, still resolvable by its quote selector, still findable through FTS, and that
`review_items` arrived empty.

## M9 verification — 2026-09-17

Shipped **EPUB-only**. M8's PDF work compiled but had never opened a PDF and had no tests,
so it is parked on the `m8-pdf` branch and main was returned to the verified M7 state
before any of this ran.

| Command | Result |
|---|---|
| `./gradlew test` | 120 tests, 0 failures |
| `./gradlew lint` | 0 errors |
| `./gradlew :app:assembleRelease` (no keystore) | **fails in 39s** with the signing message — as designed |
| `./gradlew :app:assembleRelease -Plumen.unsignedRelease=true` | BUILD SUCCESSFUL → `app-release-unsigned.apk`, **12.4 MB** |

**The done-when — "R8/minify config verified by opening a book on a *release* build" —
done literally.** The unsigned release APK was copied, signed by hand with the local debug
key *for the emulator only* (the build itself will never do this), installed under the
real `com.sidharth.lumen` id on a clean slate, and then:

1. Launched → Library drew. Hilt, Compose, Room and DataStore all survived minification.
2. Imported *The Hitchhiker's Guide to the Galaxy* through the real file picker → title,
   author and cover parsed. Readium's streamer survived minification.
3. Opened it → cover page, then five page turns into paginated, justified, sepia prose
   with italics intact. Readium's navigator and its WebView bridge survived minification.
4. Same process id start to finish; `logcat` clean of `FATAL`, `ClassNotFound`,
   `NoSuchMethod` and `NoSuchField` throughout.

Screenshots in `docs/screenshots/m9/`. The debug-key-signed test APK was never committed
and is not the artifact to install on a phone.

**Permissions, measured rather than asserted.** `dumpsys package` on the installed release
build lists **zero `android.permission.*` entries**. Two that had been merging in
transitively — `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, from ExoPlayer via Readium's
navigator, for audiobooks Lumen never plays — are now stripped in the manifest. `INTERNET`
was never present from any source. The single remaining entry is AndroidX's app-private
signature permission, documented in the README.

**Two defects found and fixed during M9:**
- The empty-library text said "Import an EPUB or PDF". PDF is not shipping. Now "EPUB".
- My own M5 note predicted a ~39MB release APK. Measured: 12.4MB. Corrected above.

**One honest caveat on the evidence.** The string fix landed after the build that was
tested. A string resource cannot change what R8 keeps or strips, so the verification
stands — but the APK the owner builds will differ from the tested one by that one string.

**Still emulator-only.** Every "on device" claim in this file remains "on emulator", and the
emulator ran at a host load average of 15–21 throughout M9 (install alone took 15
minutes). Nothing here has been watched on a physical phone. The first install on the
owner's own handset is the real M9 acceptance, and it is theirs to run.

## M10 verification — 2026-09-18

| Command | Result |
|---|---|
| `./gradlew test` | 133 tests, 0 failures (13 new) |
| `./gradlew connectedAndroidTest` | 54 tests, 0 failures (8 new) |
| `./gradlew lint` | 0 errors; 1 warning (`"%d imported"` — grammatical either way, left) |

Room schema v5 → v6 (`reading_sessions`), migration added, and `MigrationTest` now runs
the full v1→v6 chain. Every item below was checked on the emulator by something that
could fail, not by looking:

| Item | How it was proved |
|---|---|
| Underline highlights | Pixel scan of the page: one **5px** yellow band under the word, where a fill would be ~50px |
| Page flow toggle | Sheet switched to scroll; chrome then offered **Auto-scroll**, which it never does in paged mode |
| Auto-scroll | Two frames 8s apart: page content shifted **~210px** up (1474/1600 rows changed); it crossed the cover→Chapter 1 boundary on its own; a page tap paused it |
| Volume buttons | Four `KEYCODE_VOLUME_DOWN` → progress 0%→4%; `dumpsys audio` media volume **2 before, 2 after** (press consumed, no panel) |
| Chapter time | After four real turns ~5s apart the chrome showed **"~4 min left in chapter"**; nothing shown before the pace settled |
| Keep screen on | `dumpsys window`: `fl=KEEP_SCREEN_ON` on Lumen's window in the reader, **absent** back in the library |
| Orientation lock | `dumpsys activity`: `requestedOrientation=SCREEN_ORIENTATION_LOCKED` in the reader, `UNSPECIFIED` after leaving |
| Warm tint | Compose UI test samples the rendered pixel: red > blue by >40, blue still >140 (amber, readable); off → pure white |
| Reading stats | Three sessions in the table (1063s/10 pages, 20s, 31s) → screen shows **18 min, 10 pages, 33 pages/hour**, today's bar lit, 1-day streak — arithmetic reconciles exactly |
| Folder scan | Picked `Download/Books`; **"Found 3 books"** including one in a subfolder, labelled with its path; imported all three; no duplicate hashes |
| Multi-select picker | `OpenMultipleDocuments`; batch snackbar wording covered by string resources |
| Haptics | Calls are wired and gated by the setting; **not verifiable on an emulator** (no motor). Owner judges strength on the phone |

Screenshots in `docs/screenshots/m10/`.

**Bugs found and fixed during M10:**
- **Day bucketing was 5½ hours off** on an Asia/Kolkata device — SQLite read a local-midnight
  string back as UTC. Caught by `ReadingSessionTest` on the emulator, which happens to be
  set to the owner's zone. Fixed with the `'utc'` modifier.
- **The Aa sheet's last slider sat in the gesture-navigation strip**, where a drag is read
  as a system swipe and dismisses the sheet. Fixed with bottom padding.

**Platform limits found:**
- Android will not grant folder access to `Download` itself or the storage root; a
  subfolder such as `Download/Books` works. Documented in the scan sheet's empty state and
  the README.
- The tree picker opens on "Recent" for a fresh install; the user navigates from there.

**Honest limits of the emulator evidence:** uiautomator lagged badly throughout (dump and
screenshot frequently disagreed), so several checks above are from `dumpsys`, the
database, or pixel maths rather than from driving the UI. Nothing in this milestone has
been felt on a phone; haptics in particular need the owner's thumb.

## M10.1 verification — 2026-09-18 (1.2.0)

| Command | Result |
|---|---|
| `./gradlew test` | 141 tests, 0 failures (8 new: `ReadingInsightsTest`) |
| `./gradlew connectedAndroidTest` | 54 tests, 0 failures |
| `./gradlew lint` | 0 errors; 1 deliberate warning |

Bottom bar with **Home** and **Library**; Home is the dashboard. Verified on the emulator
against a seeded month of sessions (synthetic, for the visual only): hero minutes with a
14-day sparkline and yesterday delta; KPI row; 16-week heatmap with today ringed;
weekday averages with today emphasised; 24-bin hour histogram with the peak emphasised
and named; pace line with dashed mean and a ringed latest point; per-book bars with
progress; records. Tab switch round-trips and restores each tab's scroll state.

**Fixed during verification:** the nested Scaffolds each applied the status-bar inset,
doubling the gap above the title; and "pages per hour" overflowed its KPI tile.

Screenshots `60–62` in `docs/screenshots/m10/`.

## M11 verification — 2026-10-04 (1.3.0)

| Command | Result |
|---|---|
| `./gradlew test` | 150 tests, 0 failures (9 new) |
| `./gradlew connectedAndroidTest` | 54 tests, 0 failures |
| `./gradlew lint` | 0 errors; 2 deliberate warnings |

**Read-aloud, on the phone's own voices.** Readium's `TtsNavigator` with its bundled
`AndroidTtsEngine`; the sentence being spoken is tinted on the page and the page follows
it. Speed, pitch, voice picker and a sleep timer. Verified on the emulator against
*Hitchhiker's Guide*:

- Google TTS synthesised with an **embedded (offline) voice** —
  `TTS dispatch: en-us-x-iog-seanet-embedded` — and Readium's `AudioFocusManager` took
  audio focus as `USAGE_MEDIA/CONTENT_TYPE_SPEECH`.
- **Background playback proved by counting synthesis requests:** 2 in 20s with the reader
  in front, then **3 more in 25s with the app backgrounded**, process still alive, service
  still `isForeground=true types=0x2 (mediaPlayback)`.
- The page visibly followed the voice from the cover into Chapter 1.

**Permissions went from zero to three**, all for background audio, all recorded in
DECISIONS.md and spelled out in the README. `INTERNET` is still absent; ExoPlayer's two
are still stripped. Verified on the built APK: `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, and nothing else.

**Book search is now remembered folders**, not a whole-phone sweep — the owner was offered
`MANAGE_EXTERNAL_STORAGE` with its real cost and declined it. First search asks for a
folder; every later one is a single tap across all remembered folders, de-duplicated.

Screenshots in `docs/screenshots/m11/`.

**Three defects found and fixed during verification:**
- **Background playback silently didn't work.** `MediaSessionService` never reached the
  foreground, because it only raises its notification once *its* player-state logic is
  satisfied and Readium's TTS adapter doesn't drive it that way. Android killed the whole
  process seconds after backgrounding — with no crash in the log, which is why only the
  synthesis-request count caught it. Fixed by calling `startForeground` directly with a
  MediaStyle notification.
- **The voice picker listed 256 voices**, led by Arabic for an English novel and labelled
  with engine ids. Now ranked by the book's language, labelled from the locale, with the
  rest behind a toggle.
- **A flaky theme test** was asserting selection immediately after a click, racing a
  DataStore round-trip. It now waits for the state it expects.

**Honest limits.** The emulator has no audible output here, so "it speaks" rests on the
synthesis requests, the audio-focus grant and the AudioTrack players — not on hearing it.
**Whether the voice is good enough is the open question**, and only the owner can answer
it; that answer decides whether Piper is worth 72MB. The emulator also logged
`AudioHardening background playback would be muted for com.google.android.tts`, an
Android 16 behaviour aimed at background apps; our foreground service holds focus, but it
has not been observed on real hardware.

## Open questions for the owner
- **Listen to read-aloud and judge the voice.** That is the whole decision on Piper: if
  your phone's voice is good enough, 1.3.0 is done and the APK stays ~13MB; if it is not,
  swapping in Piper costs ~72MB and is a contained change behind the same interface. Try
  the voice picker too — the emulator offered many English variants and some are much
  better than others.
- **Vertical scrolling already exists** (Aa → Page movement → Scroll). Say if you want it
  to be the default for new books rather than page-turning.
- Three permissions now, all for background audio. If you would rather have none, say so
  and read-aloud becomes foreground-only again.
- Still emulator-only; nothing in this milestone has been heard on a phone.
- PDF (`m8-pdf`) remains parked. Notes' date-range filter still has no picker UI.
