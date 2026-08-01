# Decisions

One entry per non-obvious choice. Newest last. Format: date — decision, rejected alternative, why.

## 2026-08-01 — Single Gradle module, package-separated
Chose one `:app` module with feature packages over 3–5 feature modules. Rejected
multi-module because a solo project pays the Gradle-config and Hilt-wiring cost of module
boundaries without getting the payoff (parallel team work, build-time isolation). If build
times become painful, `:core:database` is the first split.

## 2026-08-01 — Readium Kotlin toolkit for EPUB
Per spec §3. Rejected hand-rolled WebView pagination: the locator model (href +
progression + text-quote selector) is exactly the anchoring primitive M4 needs, and
rebuilding it is the multi-week failure mode this project is trying to avoid.
Cost accepted: Readium renders into a WebView, so the M3 gesture layer must cooperate
with it rather than sit naively on top. License BSD-3-Clause.

## 2026-08-01 — Homebrew `openjdk@21` formula, not the Temurin cask
Machine had no JDK at all. Rejected `--cask temurin@21` because casks that install into
`/Library/Java/JavaVirtualMachines` need an admin password, which this session cannot
supply. The formula installs under `/opt/homebrew` with no sudo. JDK 21 (not 17) because
AGP 8.13 targets it and it is the current LTS.

## 2026-08-01 — Open English WordNet as the bundled dictionary
Chose OEWN 2024 → prebuilt SQLite + FTS asset in the APK (~25–30MB, CC BY 4.0).
Rejected Wiktionary/kaikki extracts (far richer, but multi-GB — unshippable in an APK and
would force the first-run download the spec wants to avoid). Consequence worth noting: the
app declares **zero** permissions, because the web escape hatch is an `ACTION_VIEW` intent
and needs no `INTERNET`.

## 2026-08-01 — AGP 9.3.1 + compileSdk 37, and no `kotlin-android` plugin
`sdkmanager` shows `platforms;android-37.1` as the current stable platform, so
"target the current stable SDK" (§3) means 37, and only AGP 9 knows API 37. Rejected the
safer-looking AGP 8.13.2 + compileSdk 36 because it would have shipped a knowingly stale
target on day one.

Consequence discovered on the first build: **AGP 9 supplies Kotlin itself and hard-errors
if `org.jetbrains.kotlin.android` is also applied.** The plugin is gone from the catalog
and both build files; `org.jetbrains.kotlin.plugin.compose` is still applied explicitly.
The `kotlin { compilerOptions { jvmTarget } }` block still works under AGP's built-in
Kotlin, so JVM target 21 is still set in one place.

## 2026-08-01 — Withhold the first frame instead of guessing a theme
§2.7 forbids a flash of the wrong theme. DataStore reads asynchronously, so the theme is
not known when the first frame would be drawn. `MainViewModel` exposes `AppSettings?` and
`MainActivity` composes nothing while it is null; the window keeps its
`Theme.Lumen.Starting` background, which is duplicated in `values/` and `values-night/`
so it already matches the system light/dark. Rejected `core-splashscreen` (a dependency
for one frame) and rejected a blocking `runBlocking { first() }` read on the main thread.
Residual imperfection, stated plainly: if the user picks Sepia or AMOLED *and* their
system is in the opposite light/dark mode, that first frame or two is the system-coloured
background rather than their palette. Revisit if it is visible in practice.

## 2026-08-01 — `allowBackup="false"`, both backup configs declared
Nothing goes to Google's cloud backup or device-to-device transfer. `allowBackup="false"`
covers cloud backup on all API levels, but on Android 12+ D2D transfer is governed
separately, so `data_extraction_rules.xml` excludes everything, and `backup_rules.xml`
states the same intent for pre-12. The user's real safety net is the explicit
backup/restore file in M6, which they control.

## 2026-08-01 — Core library desugaring, forced by Readium
Readium 3.3.0's AARs declare `requires core library desugaring`, so the build refuses to
package them without `com.android.tools:desugar_jdk_libs`. This is not a discretionary
dependency — it is a precondition of a library §3 mandates — so it was added rather than
blocking the milestone on an approval. It is Google's own backport of `java.time`,
`java.util.stream` and friends, ~2MB before shrinking (R8 strips the unused parts in
release), licensed GPLv2 **with the Classpath Exception**, which is why it is safe to link
into a closed application.

## 2026-08-01 — Content hash is a book's identity; highlights point at it, not at a row id
`HighlightEntity.bookContentHash` references the SHA-256 of the file rather than
`books.id`, and there is deliberately **no foreign key**. This makes three requirements
fall out for free instead of needing special cases: deleting a book can leave its
highlights behind (§2.1), re-importing the same file silently reunites them (§2.2), and
"the same book" survives a rename or a different storage path. Rejected an FK with
`ON DELETE SET NULL`, which would have made orphaned highlights unattributable.

## 2026-08-01 — Highlight table exists in schema v1, five milestones before highlighting
Not speculative: M1 must ask what happens to a book's highlights on delete, which needs
somewhere to count them. Beyond that, §4 says any migration risking highlights must be
proved safe, so the durable columns are defined once rather than added later by a
migration that could lose data. Notes, tags, lookups and review items are *not* here —
they arrive with their own milestones.

## 2026-08-01 — The file picker offers every type, then validates by parsing
`OpenDocument(arrayOf("*/*"))` rather than filtering on `application/epub+zip`. Many
Android file providers report EPUBs as `application/octet-stream`, and a filtered picker
hides them — which looks to the user like the file has vanished. Instead the file is
copied, then handed to Readium; if it will not open, the import fails with a readable
message and nothing is left behind. The gate is "can this actually be read", which is the
only gate that matters.

## 2026-08-01 — Copy and hash in a single pass, into staging
`ContentHasher.copyAndHash` digests while streaming to a staging file, so a 40MB EPUB is
read once and never held in memory (§5). Parsing happens against the staging copy, so a
file that turns out not to be an EPUB never reaches the library directory. Only after a
successful parse is it renamed into place under its hash.

## 2026-08-01 — Readium wired with a no-network HttpClient and the EPUB parser only
`NoNetworkHttpClient` fails every request instead of supplying `DefaultHttpClient`, so a
stray attempt to fetch over the network surfaces as a clear error rather than a confusing
`SecurityException` from the missing INTERNET permission. `EpubParser` is used directly
rather than `DefaultPublicationParser`, whose composite pulls in a PDF parser needing a
`PdfDocumentFactory` that does not exist until M8. Importing only what can actually be
opened keeps the "not built yet" boundary honest — PDFs are refused today, by design.

## 2026-08-01 — Atkinson Hyperlegible as the dyslexia-friendly face
Rejected OpenDyslexic: licensing history is muddier and its rendering at small sizes is
worse. Atkinson Hyperlegible is SIL OFL 1.1, designed by the Braille Institute for low
vision, and disambiguates the same character pairs.
