# Lumen

A personal EPUB reader for Android. One user, one phone, no cloud.

It is built for reading comfort and for owning your own data: everything you import,
highlight, note or look up stays in the app's private storage on the device, and the
app has no way to send it anywhere.

## Privacy — the facts, not the intent

These are things you can verify yourself from the installed app or the source, in that
order of trust.

- **Three permissions, all for one feature.** `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS` exist so read-aloud can
  keep playing once the screen is off, and so the lock screen can show play/pause.
  Android allows background audio no other way. None of them grants access to files, the
  network, the microphone or anything about you, and the notification permission is asked
  for only when you first press Read aloud — decline it and speech still works while the
  app is open. Before 1.3.0 the list was empty; if you don't want read-aloud, nothing
  here is ever used.
- **Still no `INTERNET`.** The app does not declare it, so the operating system will not
  let Lumen open a network connection at all — there is no setting to get wrong.
  Read-aloud is your phone's own speech synthesis; no text leaves the device, and voices
  that would need the network are hidden from the picker because they could only fail.
  Two permissions that a library asked for (`ACCESS_NETWORK_STATE` and `WAKE_LOCK`, from the
  audio player inside the EPUB engine, which Lumen never uses) are explicitly stripped in
  [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). For completeness: `aapt dump
  permissions` on the APK shows one entry, `com.sidharth.lumen.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
  That is not a system permission — it is a private, signature-only permission AndroidX
  defines *inside* the app so that its own broadcast receivers cannot be reached by other
  apps. It grants Lumen nothing and never appears in Settings.
- **No analytics, no crash reporting, no ads, no accounts.** Not disabled — absent. There
  is no SDK in the dependency list that could do any of these; see
  [`gradle/libs.versions.toml`](gradle/libs.versions.toml) for the complete list.
- **The dictionary is on the device.** WordNet 3.1 and CMU pronunciations ship inside the
  APK (~21 MB), so lookups work in airplane mode and never leave the phone.
- **The one thing that touches the web is you, on purpose.** The definition sheet has a
  "search the web" link. It hands the word to your browser with a normal open-URL intent;
  Lumen itself still has no network access. Nothing happens unless you tap it.
- **Android backup is switched off** (`allowBackup="false"`, with cloud backup and
  device-to-device transfer both excluded in
  [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)), so
  Google's backup never sees your library. Your safety net is the explicit
  **Backup / Restore** on the Notes screen, which writes a single ZIP to a location you
  choose.
- **Where your data lives:** `/data/data/com.sidharth.lumen/files/` — books, covers, the
  database and settings. Only the app (and you, with a rooted phone or a backup ZIP) can
  read it. Uninstalling deletes all of it.

## What's in it

- **Library** — import EPUBs through the system file picker, sorted by recent or title,
  searchable, with covers and progress.
- **Reader** — paginated, four themes (light, sepia, dark, AMOLED black), font family and
  size, line spacing, margins, justification, table of contents, position that survives
  closing the app.
- **Gestures** — tap zones for page turns, edge drags for brightness and font size,
  configurable in Settings with a live preview. Optional two-finger tap to toggle theme
  (off by default).
- **Highlights & notes** — four colours, notes and tags on any highlight, anchored so
  they survive font and layout changes.
- **Dictionary** — tap a word for its definition and pronunciation, offline, with
  lemmatisation ("running" → "run", "mice" → "mouse").
- **Notes screen** — every highlight, note and looked-up word across all books in one
  searchable, filterable list; tap any entry to jump to that spot in the book. Export to
  Markdown, CSV or JSON. Backup and restore.
- **Review** — a daily queue of your highlights and looked-up words as flashcards, on a
  simple 1 / 3 / 7 / 21 / 60-day spaced schedule.
- **Reading comforts** (1.1.0) — pages that turn *or* scroll, your choice; highlights as
  fills or underlines; a warm amber tint for night; volume buttons to turn pages; keep the
  screen on while you read (with an idle timeout); lock the orientation; a haptic tick on
  selecting and saving; and "~12 min left in this chapter", measured from your own pace.
- **Auto-scroll** (1.1.0) — in scroll mode, a play button starts the page moving at a
  speed you set. Tap to pause, tap again to carry on; it crosses chapters by itself.
- **Home tab** (1.2.0) — the app opens on your reading dashboard: today's minutes with a
  two-week sparkline, streak, pages per hour, a 16-week calendar heatmap, which weekdays
  and hours you actually read, your pace over recent sittings, time per book, and
  records. All local; it lives in the same database as everything else and rides along
  in your backup. The Library is the second tab.
- **Read aloud** (1.3.0) — a speaker button in the reader. It uses the voices already on
  your phone, highlights each sentence as it speaks and keeps the page in step, and has
  speed, pitch, a voice picker and a sleep timer. It keeps playing with the screen off,
  with play/pause on the lock screen.
- **Find books on this phone** (1.3.0) — grant a folder once and Lumen remembers it;
  after that the search button sweeps every remembered folder in one tap. Add more
  folders any time. The ordinary picker is also multi-select.

### Not in this build

- **PDF.** Rendering is written and compiles but has not yet run on a device or been
  tested, so it is held on the `m8-pdf` branch rather than shipped. EPUB only for now.
- **Cloud sync, accounts, multiple users.** By design, permanently.

### Where to keep your books

Lumen cannot search the whole phone, and that is deliberate: Android offers only one
permission for it — "All files access" — which grants read/write to everything you own
and cannot be narrowed to "just EPUBs". Instead, the search button asks for a folder the
first time and then remembers it, so every later search is a single tap across every
folder you've added.

Android refuses to hand over `Download` itself, or the top of internal storage ("To
protect your privacy, choose another folder") — so keep books in a **subfolder**, such as
`Download/Books`. Anything inside it, however deep, is found.

## Install on your phone

You need the signed APK (see *Building a release* below — the file will be
`app/build/outputs/apk/release/app-release.apk`). Requires Android 8.0 or newer.

1. Copy the APK to the phone (USB, or any file transfer you trust).
2. Open it from the phone's Files app. Android will ask you to allow installs from that
   app the first time; that is per-source and can be revoked afterwards.
3. Tap Install.

Or with a USB cable and developer mode on the phone:

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Updates install over the top and keep your library, as long as they are signed with the
same key — which is why the key matters (next section).

## Signing

Lumen is signed with **your** key. The repository never contains one, never generates
one, and the build will not stand in with the debug key if yours is missing.

**Make the keystore once, keep it forever.** If you lose it you cannot update the app in
place; you would have to uninstall (losing the library) and reinstall.

```bash
keytool -genkeypair -v \
  -keystore lumen-release.jks \
  -alias lumen \
  -keyalg RSA -keysize 4096 \
  -validity 36500
```

Then:

```bash
cp keystore.properties.example keystore.properties
```

and fill in the two passwords. Both `lumen-release.jks` and `keystore.properties` are
gitignored — confirm with `git check-ignore -v keystore.properties lumen-release.jks`
before you type a password into either.

Back the `.jks` file up somewhere that is not this repository and not this laptop.

## Building a release

```bash
./gradlew :app:assembleRelease
```

With signing configured this produces `app/build/outputs/apk/release/app-release.apk`,
shrunk with R8. Without it the build stops immediately with a message saying so.

Requirements: JDK 21 and the Android SDK (`compileSdk 37`). The first build downloads
dependencies and is slow; subsequent builds are incremental.

To run the tests:

```bash
./gradlew test                    # unit tests, on the JVM
./gradlew connectedAndroidTest    # instrumented, needs an emulator or device
./gradlew lint
```

## Project layout

Single Gradle module, packages by feature. Every non-obvious choice is written down in
[`DECISIONS.md`](DECISIONS.md) with what was rejected and why, and every milestone's
verification is in [`PROGRESS.md`](PROGRESS.md). Read those first if you come back to
this in a year.

The offline dictionary is built from WordNet 3.1 (Princeton, its own permissive licence)
and the CMU Pronouncing Dictionary (BSD) by
[`tools/build_dictionary.py`](tools/build_dictionary.py).

## Licences of what's inside

| Component | Licence |
|---|---|
| Readium Kotlin toolkit | BSD-3 |
| WordNet 3.1 | WordNet licence (permissive) |
| CMU Pronouncing Dictionary | BSD |
| Atkinson Hyperlegible font | SIL OFL 1.1 |
| AndroidX, Room, Hilt, Coil, `desugar_jdk_libs` | Apache 2.0 |
