# Lumen

A personal EPUB reader for Android. One user, one phone, no cloud.

It is built for reading comfort and for owning your own data: everything you import,
highlight, note or look up stays in the app's private storage on the device, and the
app has no way to send it anywhere.

## Privacy — the facts, not the intent

These are things you can verify yourself from the installed app or the source, in that
order of trust.

- **Zero permissions.** Open *Settings → Apps → Lumen → Permissions* on the phone: the
  list is empty. The app does not declare `INTERNET`, so the operating system will not
  let it open a network connection at all — there is no setting to get wrong. Two
  permissions that a library asked for (`ACCESS_NETWORK_STATE` and `WAKE_LOCK`, from the
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

### Not in this build

- **PDF.** Rendering is written and compiles but has not yet run on a device or been
  tested, so it is held on the `m8-pdf` branch rather than shipped. EPUB only for now.
- **Cloud sync, accounts, multiple users.** By design, permanently.

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
