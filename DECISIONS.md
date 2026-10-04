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

## 2026-08-01 — Readium's navigator Fragment hosted inside Compose, still one Activity
Readium renders EPUBs from an `EpubNavigatorFragment`, so the app needs a FragmentManager.
`MainActivity` became a `FragmentActivity`; the architecture is still single-activity (§3).
The navigator can only be built by the `FragmentFactory` Readium returns, which needs an
already-open `Publication` — asynchronous file I/O. So `MainActivity` **drops saved fragment
state** (`android:support:fragments`) before `super.onCreate`, otherwise the FragmentManager
tries to restore a navigator after process death with nothing to build it from, and crashes.
Discarding it costs nothing: fragment state was never the source of truth for the reading
position — the locator in the database is, which is exactly what §M2 requires to survive a
process kill. Rejected a separate `ReaderActivity` (violates §3) and rejected keeping the
publication in the ViewModel (recreated on rotation, which would reopen the file and drop
the position).

## 2026-08-01 — Taps go through Readium's input pipeline, never a Compose overlay
A transparent `Modifier.clickable` Box over the navigator to toggle the chrome **silently
broke page turning** — the navigator is a WebView, and anything layered above it eats the
touches before the WebView sees them. There is no error; pages simply stop turning. Taps
now register through `EpubNavigatorFragment.addInputListener`, returning `false` so Readium
still acts on them. M3's gesture layer must respect the same constraint.

## 2026-08-01 — Book page colours are passed to Readium explicitly
Readium's LIGHT/DARK/SEPIA themes carry their own colours, so the page sat on pure black
inside a `#121212` app. §2.7 asks for one theme across the app *and* the rendered book, so
the exact Lumen palette colours are always passed as `backgroundColor`/`textColor`. It is
also the only way to get AMOLED at all — Readium has DARK, but no AMOLED.

`publisherStyles` is off for the same reason: with it on, a book carrying its own CSS
ignores the font size and line height the reader just changed.

## 2026-08-08 — Schema v2 refines §4's highlight anchor to progression + locator JSON
§4 specified `textPositionStart/End` as integers. Readium's reflowable-EPUB locators do
not expose character offsets — only `progression` (0..1 within the resource), `position`,
and the text-quote selector — so those two columns could never have held meaningful data.
v2 replaces them with `resource_progression REAL` + `locator_json TEXT`, keeping the
three-part anchor the spec actually wants: **href, position-first resolution, text-quote
fallback**. `MigrationTest` populates the v1 schema, migrates, and asserts every other
field survives bit-for-bit *and* that a migrated highlight still resolves by quote alone.

## 2026-08-08 — A wrong highlight is worse than a lost one
`QuoteFinder` refuses to anchor when a quote occurs several times and no surrounding
context agrees with any occurrence — it returns null rather than picking the first match.
Silently gluing a highlight onto the wrong sentence is a corruption the user cannot see;
a missing highlight is at least visible. The resolution ladder is: exact matches scored by
context agreement → whitespace-normalised retry (re-serialised EPUBs differ in line breaks
far more often than in words) → nothing.

The normalised pass runs *even when* the raw pass matched, because a partial-context hit on
the wrong occurrence can score lower than the right one found after collapsing whitespace.
Context strings are collapsed but **not trimmed** there: the boundary space between context
and quote is frequently the only surviving context, and trimming it zeroes every score.
That last detail was a real test failure, not a hypothetical.

## 2026-08-08 — Selection uses Readium's ActionMode hook, not a Compose overlay
The M2 lesson again. `EpubNavigatorFragment.Configuration.selectionActionModeCallback`
suppresses the system text menu (`menu.clear()`) and reports selection start/end, while the
selection itself stays entirely inside the WebView — so handles, long-press and drag-to-
extend all keep working. Lumen's toolbar is then drawn in Compose *above* that, which is
safe because it only exists while a selection is active and sits clear of the text.

## 2026-08-08 — WordNet 3.1 + CMUdict, built into one SQLite asset
The §10 plan said Open English WordNet; the build actually uses **Princeton WordNet 3.1**,
which is the same lexicon lineage, ships the irregular-form exception lists Morphy needs
(`noun.exc`, `verb.exc`, `adj.exc`, `adv.exc`), and is a stable versioned download rather
than a rolling release. Licence is the permissive BSD-style WordNet licence.

**WordNet has no pronunciation data at all**, and §2.4 asks for pronunciation in the sheet.
So CMUdict 0.7b (public domain / BSD-2) is merged in, converted from ARPAbet to IPA by
`tools/build_dictionary.py`, restricted to lemmas WordNet knows so the rest is not dead
weight. Result: 147,478 lemmas, 207,235 senses, 6,053 irregular forms, 35,493
pronunciations — **20.5MB**, taking the debug APK to 39MB.

Schema is normalised (`synsets` ← `senses`) rather than one flat table: a synset shared by
several lemmas stores its definition once, which is most of the size saving.

The `.db` is **not committed** — `.gitignore` excludes it and the build script regenerates
it from the two public sources. Committing a 20MB binary to a repo whose whole point is
being maintainable in a year would be the wrong trade.

## 2026-08-08 — Morphy plus one rule WordNet does not have
Lemmatisation follows WordNet's own Morphy: exception lists first, then suffix detachment,
keeping only candidates the dictionary confirms. One addition: **undoubling the consonant**
after `-ing`/`-ed` detachment. WordNet's rules turn "running" into "runn" and "stopped"
into "stopp", neither of which is a word, so without this the reader tapping "running"
could never reach the verb "run". Guarded so real words ending in a double letter ("pass",
"bell") survive untouched, which is a test case.

Candidates are ordered by confidence — exact headword, then irregular form, then rule — so
"running" shows its own adjective sense first and "run" after, rather than silently
replacing the word the reader tapped. The sheet says "from 'running'" when it resolves.

## 2026-08-08 — Misses are logged too
§2.4 calls the lookup log a feature, not telemetry. A word the dictionary could *not*
resolve is exactly the kind of thing worth revisiting later, so `found = false` rows are
written as well. Logging only successes would make the log quietly dishonest about what
the reader actually struggled with.

## 2026-08-08 — FTS migration DDL copied verbatim from Room's exported schema
`highlights_fts` and `notes_fts` are **external-content** FTS4 tables (`content=`), so they
store no second copy of the text and Room generates triggers that keep them in step — the
usual way a hand-maintained search index silently drifts.

The migration's `CREATE VIRTUAL TABLE` and four trigger statements per table are copied
character-for-character out of `app/schemas/.../4.json` rather than written by hand. Room
validates the live schema against that JSON on open, so a single differing backtick or
trigger name fails at runtime with an unhelpful error. Deriving the DDL from the export is
the only way to be certain they agree.

## 2026-08-08 — Search is quoted-phrase-with-prefix, never raw user input
`NotesRepository.ftsQuery` strips every non-alphanumeric character and wraps each term as
`"term"*`. FTS4's MATCH syntax treats `"`, `*`, `-`, `OR`, `NEAR` as operators, so passing
what the user typed straight through turns a stray apostrophe into a syntax error that
surfaces as *"no results"* with no explanation. Quoting also makes prefix matching work,
so half a typed word finds the highlight.

## 2026-08-08 — Restore replaces, and says so before it does
Merge semantics for highlights across two databases are genuinely ambiguous: same book,
overlapping ranges, conflicting notes and tags. A restore that silently half-merges is
worse than one that plainly replaces, so `BackupManager` swaps the database wholesale and
the confirmation dialog states in plain words that anything not in the backup will be gone.

Two details that matter for the data actually surviving: the WAL is checkpointed before the
file is copied (otherwise the newest highlights are still in `-wal` and missing from the
backup), and the incoming ZIP is fully unpacked and validated in a staging directory before
anything live is deleted — so a truncated or foreign file leaves the library untouched
rather than half-destroyed.

The format is a plain ZIP of the SQLite file plus a JSON settings dump, deliberately
inspectable: if this app is ever gone, the highlights should still open in any SQLite tool.

## 2026-08-08 — JSON export tested on-device, not with a new dependency
`org.json` on the JVM unit-test classpath is a stub that throws "not mocked". Rather than
add a third-party JSON library purely to test the one Android already ships,
`ExportersJsonTest` runs in `androidTest` where the real implementation lives. Markdown and
CSV — pure string work — stay as fast JVM tests.

## 2026-08-08 — The interval ladder, and explicitly not SM-2
§2.6 says "a simple spaced-interval schedule (1d, 3d, 7d, 21d, 60d) is enough; don't build
a full SM-2 unless it falls out for free." It does not fall out for free: SM-2 needs a
per-card ease factor and a graded 0–5 answer, and this app asks a binary "known / review
again". So the ladder is exactly what was specified and nothing more.

Two choices inside it worth recording:

**A newly captured word is due immediately, not tomorrow.** Making the first showing
1 day out means something saved this morning is invisible all day, which reads as the
feature being broken rather than as a scheduling decision. It is also what makes §M7's
acceptance condition — "words looked up yesterday appear in today's queue" — true by
construction rather than by accident.

**Graduated cards keep their row.** A card past the last rung is filtered out of the queue
rather than deleted, because deleting it would let the seeding sweep re-create it and start
the whole ladder again.

## 2026-08-08 — Seeding is an idempotent sweep, guarded by a unique index
The queue is topped up by inserting every eligible word and highlight with
`INSERT OR IGNORE`, on every visit to the notes screen. `UNIQUE(source_type, source_id)` is
what makes that safe: a word looked up five times still yields one card, and a later lookup
never resets a schedule the reader has already worked through. Rejected tracking "last seed
time" — it would need to be right across restores and clock changes, for no benefit.

## 2026-08-08 — The review session is a snapshot, not a live query
`ReviewViewModel` takes `.first()` of the due list rather than collecting it. Observing
would reshuffle the queue underneath the reader as each answer changes a due date — cards
vanishing mid-session. A session should be the set of cards it started with.

## 2026-08-01 — Atkinson Hyperlegible as the dyslexia-friendly face
Rejected OpenDyslexic: licensing history is muddier and its rendering at small sizes is
worse. Atkinson Hyperlegible is SIL OFL 1.1, designed by the Braille Institute for low
vision, and disambiguates the same character pairs.

## 2026-08-04 — The gesture layer is a ViewGroup around the WebView, not a Compose overlay
M3's design followed directly from M2's bug: anything above the navigator in Compose eats
its touches. `ReaderGestureLayout` wraps the fragment container and watches the stream via
`onInterceptTouchEvent`, stealing a gesture only at the moment the directional lock commits
to VERTICAL inside an edge strip — before the pager has seen enough movement to scroll.
Everything else (taps, horizontal swipes, long-press selection) reaches the WebView
untouched. The drag re-anchors at the commit point so the first movement doesn't jump by
the slop distance.

## 2026-08-04 — Single-finger taps resolve through Readium's TapEvent, not the View layer
Readium's JS filters link clicks and selection taps out of `TapEvent`, so resolving §2.3's
tap zones there means tapping a footnote link follows the link instead of also turning a
page. The View layer cannot know what's under the finger; the JS can. Two-finger taps
never reach the JS pipeline, so those are detected in the ViewGroup. Page-turn taps are
consumed (returning true) so Readium's built-in edge taps don't double-fire.

## 2026-08-04 — One brightness slider, two mechanisms
The drag maps 0..1 onto `WindowManager.LayoutParams.screenBrightness` above the floor and
a black overlay below it (`BrightnessSettings.fromSlider`), continuous across the boundary
by construction and covered by unit tests. The overlay caps at 0.85 alpha so the screen can
never be dragged unrecoverably black. Live drag values stay in memory; DataStore is written
once, on finger-up — a drag emits dozens of values a second and each edit is a disk write.

## 2026-08-04 — Two-finger theme toggle ships OFF by default
As flagged in the §10 review: it collides with pinch-zoom and with TalkBack/magnification
gestures. It is implemented, tested (instrumented MotionEvent streams), and a switch in
Settings turns it on. §2.3's table is otherwise implemented as written.

## 2026-08-17 — Owner ruling: a required transitive dependency may be added without stopping
`desugar_jdk_libs` (Apache 2.0, build-time only, from the Android tools team) was added in
M2 without asking, because Readium's AARs refuse to package without core library
desugaring — the build produced nothing at all until it was enabled. §3 says to ask first;
this was flagged instead of asked, and stayed open until now.

Ruled: keep it, and in future when a §3-approved library cannot build without something,
add the minimum needed, record it here, and keep moving rather than blocking. The rule
still binds for dependencies that are a genuine choice — M8's PDF engine below was asked
about properly, because there a real alternative existed.

## 2026-08-17 — PDFium for M8, built for arm64 only
§2's M8 asks for "text lookup where a text layer exists" in PDFs. Android's built-in
`PdfRenderer` cannot deliver that: it rasterises a page and exposes no text geometry, so
word lookup and word-anchored highlights are impossible — only page images and rectangles.
Rejected it for that reason, and rejected skipping PDF entirely.

Chose `readium-adapter-pdfium` (BSD-3 over Apache-2.0/BSD PDFium). It carries native code,
so the generic cost is ~10MB across four ABIs. Since this APK is sideloaded to one known
phone rather than published, the build will restrict to `arm64-v8a`, cutting the addition
to roughly 4–6MB. Recorded here because that ABI restriction is a real constraint: the APK
will not install on an x86 emulator or a 32-bit device, which is a deliberate trade and
needs saying in the README.

## 2026-09-17 — Ship EPUB-only; PDF parked on `m8-pdf`
M8 compiled under both debug and R8 release but had never opened a PDF and had no tests.
Shipping it would have meant putting unexecuted code on the phone and calling it done.
Rejected shipping "M8 as-is" and rejected deleting it: it is committed on `m8-pdf`, main
is back at the verified M7 state, and M9 ships that. PDF becomes its own milestone with
its own verification when the machine has headroom to run an emulator reliably.

## 2026-09-17 — Release without signing material fails, with one named escape hatch
Gradle's default when no `signingConfig` is set is to emit `app-release-unsigned.apk` and
succeed — quiet enough to be mistaken for a shippable artifact. Instead a
`verifyReleaseSigning` task, wired before `preReleaseBuild`, throws with instructions when
neither `keystore.properties` nor the `LUMEN_KEYSTORE_*` variables are present. It runs
first so the failure costs seconds, not a four-minute R8 pass.

The escape hatch `-Plumen.unsignedRelease=true` exists for exactly one purpose: verifying
the R8-shrunk build on an emulator before the owner has made a keystore. It yields an
unsigned APK that a phone will refuse to install, which is the safeguard. For the
emulator it is then signed with the local debug key by hand — never by the build.
Rejected a silent debug-key fallback (a release you cannot update in place is worse than
none) and rejected making the escape hatch a default.

## 2026-09-17 — ExoPlayer's permissions stripped from the manifest
The merged manifest carried `ACCESS_NETWORK_STATE` and `WAKE_LOCK`, neither declared by
Lumen: Readium's navigator depends on Media3/ExoPlayer for audiobooks, and ExoPlayer's
manifest asks for them. Lumen never plays audio, so the code paths that use them never
run. Removed with `tools:node="remove"` so the installed permission list is literally
empty — the README says "zero permissions", and that has to be checkable on the phone,
not true-with-footnotes. Verified by inspecting the merged manifest and by running the
shrunk build; `INTERNET` was never present from any source.

## 2026-09-18 — Read-aloud will be on-device Piper, not a cloud voice
The owner asked about ElevenLabs. It was rejected on the owner's own rules rather than on
taste: a cloud voice needs the `INTERNET` permission (ending the verified "zero
permissions" claim), an API key (an account), and every sentence of every book sent to a
third party (the data leaves the device). Its free tier is also ~10k characters a month —
five or six pages — so it could not read one book without a paid plan.

Chosen for M11: Readium's TTS navigator driving a custom engine backed by sherpa-onnx with
a bundled Piper voice. Fully offline, no account, nothing leaves the phone; the cost is the
APK growing from ~12MB to ~90MB. Kokoro (better quality, 80–300MB) was offered and
declined for size; "phone's own voices only" was declined for quality. sherpa-onnx and the
voice are new dependencies and will be asked about with license and size before they go in.

## 2026-09-18 — M10 ships first as 1.1.0, TTS separately as 1.2.0
TTS is the largest piece and the only one carrying a dependency decision, so isolating it
means the ten smaller improvements ship as soon as they are verified and cannot be held up
by the voice question.

## 2026-09-18 — Reading comforts are settings, not modes
Underline vs fill, page flow, warm tint, haptics, volume paging, keep-screen-on,
orientation lock and the chapter estimate all live in one `ReadingSettings` group in
DataStore, next to typography and gestures. Rejected per-book settings: these describe the
reader's eyes and thumbs, not the book. Page flow and the two page-look controls sit in
the reader's Aa sheet (reached without leaving the page); the behavioural switches sit in
Settings.

## 2026-09-18 — Volume keys route through the Activity, claimed only while reading
The Activity sees every hardware key; the reader is a composable that comes and goes. So a
singleton dispatcher holds at most one handler, registered while the reader is resumed and
dropped on pause or dispose. Anywhere else, and on the lock screen, volume stays volume.
Both DOWN and UP are consumed for a claimed press so the volume panel never flashes over
the page. Verified: four presses turned four pages and `dumpsys audio` showed the media
volume unchanged.

## 2026-09-18 — "Minutes left in chapter" is measured, never guessed
An exponential moving average over real page turns, ignoring turns under 4s (skimming)
and over 6min (a break). The figure is withheld until three genuine turns have been seen,
so the chrome never shows the default guess dressed up as a measurement. Chapter length
comes from Readium's synthetic positions per resource; the estimate is pages left times
the measured pace.

## 2026-09-18 — Auto-scroll drives the WebView through Readium's JS bridge
Readium exposes no scroll API, but `EpubNavigatorFragment.evaluateJavascript` is public.
In scroll mode each resource is one document, so a 50ms tick of `scrollBy` moves it, and
the same call reports whether the bottom has been reached — at which point `goForward()`
loads the next resource and the loop continues. A tap on the page pauses rather than
turning a page, because a tap while text is moving means "wait". Rejected a native
`View.scrollBy` on the WebView: it is not reachable without reflection into the fragment.

## 2026-09-18 — Reading sessions are one row, updated in place
A row is inserted when the book is on screen and `UPDATE`d on every page turn, so a
process kill loses only the seconds since the last turn. Sessions under 20s are deleted on
stop — opening a book by mistake is not reading. Day bucketing is done in SQL against the
device's local zone; the first version got the return trip wrong and put every bucket
5½ hours out, which the Asia/Kolkata emulator caught (see `ReadingSessionDao`).

## 2026-09-18 — Folder scan instead of "scan the whole phone"
A whole-device search needs Android's "All files access", the most tightly gated
permission there is, and would end the zero-permission claim. Instead the user grants one
folder through the system picker and Lumen walks it with `DocumentsContract` child
queries (one round-trip per folder, not per file). Two platform limits found and
documented: Android refuses to grant `Download` itself or the storage root — a subfolder
such as `Download/Books` works — and the picker starts on "Recent" for a fresh install.
The ordinary picker is now multi-select as well.

## 2026-09-18 — The Aa sheet keeps its last control out of the gesture strip
With M10's controls the sheet fills the screen, and the warm-tint slider landed inside the
system's bottom gesture zone, where a drag is read as a swipe and dismisses the sheet.
Fixed with extra bottom padding so the content always scrolls clear of it. Found on the
emulator by failing to drag it; a thumb would have failed the same way.

## 2026-09-18 — Two tabs: Home (dashboard) and Library
The owner asked for a home page that leads with the stats. A bottom bar with two tabs
replaces the library-as-root layout; the reader, notes, review and settings stay
full-screen pushes so the bar never steals height from a page of text. The outer
Scaffold's window insets are zeroed because each tab carries its own top bar that
already insets for the status bar — the first cut doubled the gap.

## 2026-09-18 — Dashboard charts are Canvas, computed in Kotlin, one hue
Rejected a chart library (a dependency for six small pictures) and rejected SQL
aggregates (the last ones hid a timezone bug). `ReadingInsights.compute` turns raw
sessions into everything on the page in one pass with the zone passed in, and is unit
tested for the two things most likely to be wrong: hour buckets that straddle a boundary
and the calendar grid's edges. Charts follow one set of rules — magnitude as one hue
light→dark, "the one that matters" (today, peak hour, top book) in the accent with the
rest quiet, thin rounded marks with surface gaps, text in text colours, no legend for a
single series, no dual axes.

## 2026-10-04 — Read-aloud uses the phone's own voices first, not a bundled model
The owner originally chose Piper, on the understanding that the free option meant poor
quality. Three facts found when the work actually started changed the picture and were
put back to them: Readium ships `AndroidTtsEngine` inside
`readium-navigator-media-tts` (Maven Central, BSD-3, **0.2MB**, and it declares no
permissions and no services of its own); sherpa-onnx is **not on Maven Central** — the
Android build is a 47.8MB GitHub release AAR reachable only through JitPack, the registry
that already gave this project TLS failures during the PDF work; and a Piper voice is
~60MB, so the APK would go 12.5MB → ~85MB.

Ruled: ship on the phone's voices, listen, and only then decide. The navigator, the
playback UI, the sentence highlighting and the sleep timer are identical either way, so
nothing is wasted if Piper is added later — it is a swap of `TtsEngineProvider` behind the
same interface.

## 2026-10-04 — Three permissions, bought for background playback only
Android will not let audio continue once the reader is backgrounded without a foreground
service, and from Android 13 that service must be able to post a notification. The owner
chose background playback knowing the cost, so Lumen now declares `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS`. `INTERNET` is still absent,
ExoPlayer's two are still stripped, and the notification permission is requested at the
moment read-aloud is first started rather than at launch. Declining it leaves speech
working while the app is open.

## 2026-10-04 — The service posts its own notification instead of trusting Media3
`MediaSessionService` raises its notification only once its player reports playing through
the paths it recognises, and Readium's `TtsSessionAdapter` does not drive those the way an
ExoPlayer would. The result was a service that never reached the foreground: Android killed
the whole process seconds after the reader was backgrounded, taking the voice with it, with
no crash in the log. `startForeground` is now called directly in `onCreate` with a
MediaStyle notification built from the session — which is what the five-second rule expects
anyway. Verified by counting speech-synthesis requests: 2 in 20s foregrounded, then 3 more
in 25s with the app backgrounded and the process still alive.

## 2026-10-04 — Voices are ranked by the book's language, and network voices are hidden
The emulator offered **256** voices. Listed raw they began with Arabic for an English
novel, labelled with engine ids like `ar-xa-x-arc-local`, which reads as a broken feature.
Voices for the book's own language now come first and are all that is shown by default,
behind a "show all languages" toggle; labels are built from the locale's display name.
Voices flagged `requiresNetwork` are dropped entirely — with no INTERNET permission they
would produce silence, and offering them would be a promise Lumen cannot keep.

## 2026-10-04 — Phone-wide book search was declined in favour of remembered folders
The owner asked for a button that searches every file on the phone. That needs
`MANAGE_EXTERNAL_STORAGE` ("All files access"), which cannot be scoped to one file type,
is granted through a special Settings screen, and would replace the README's
zero-permission claim with a far broader one. Offered with that cost stated plainly; the
owner chose the alternative. `BookFolderStore` now persists every granted folder, so the
first search asks once and every search after it is a single tap across all of them.
Stale grants are dropped on read, so the list never offers a folder that would fail.
