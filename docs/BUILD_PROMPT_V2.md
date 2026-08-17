# BUILD PROMPT — "Lumen" personal EPUB reader for Android (second edition)

> This is a rewrite of an earlier build prompt, informed by actually building the thing
> once. Everything in §2–§5 is a *verified fact* that the first build had to discover the
> expensive way — by decompiling AARs, by failed compiles, and by one silent runtime bug.
> Take them as given; do not re-derive them.
>
> Paste everything below into a fresh agent session in an empty directory.

---

## 0. Role and mission

You are the sole engineer on a personal Android app. I am the only user and the only
stakeholder. Take this from empty directory to a signed APK on my phone.

Optimise for: **reading comfort, offline-first, my data stays on my device, and a codebase
I can still modify in a year.** Not for: multi-user, cloud sync, monetisation, Play Store.

**Also optimise for your own context budget.** §6 is not advice, it is a requirement — the
first build of this app spent more than half its context on things §2–§6 make unnecessary.

---

## 1. What the app is

A reader for EPUB files I sideload. It opens a book, remembers where I was, is comfortable
at night, lets me tap a word for a definition, lets me highlight and annotate, and lets me
search and review everything I've saved across all books.

### 1.1 Library
SAF import; copy into app-private storage on import; grid with cover art extracted from the
EPUB (generated title card as fallback); title/author/cover/format/dateAdded/lastOpened/%;
long-press to delete, **asking what to do with the book's highlights (default: keep)**;
sort by recent/title/author; search over title+author.

### 1.2 Reader
EPUB 2 and 3, reflowable, **paginated** (not scroll). Typography: font family (serif, sans,
a dyslexia-friendly face), size, line height, margins, alignment, hyphenation. TOC drawer.
Reading position persists per book and survives process death, font-size changes, and
re-import of the same file (**match by content hash, not filename**).

### 1.3 Gestures — the heart of the app
| Zone | Gesture | Action |
|---|---|---|
| Left ~15% edge strip | Vertical drag | Screen brightness, up = brighter |
| Right ~15% edge strip | Vertical drag | Font size, one step per ~40dp |
| Anywhere | Horizontal swipe | Prev / next page |
| Left / right ~25% tap zones | Tap | Prev / next page |
| Centre ~50% | Tap | Toggle chrome |
| Anywhere | Long-press | Text selection |
| Anywhere | Two-finger tap | Toggle light/dark (**default OFF** — collides with pinch-zoom and TalkBack) |

Brightness is **window-local** (`WindowManager.LayoutParams.screenBrightness`), needs no
`WRITE_SETTINGS`, persists, and has a "follow system" sentinel (-1f). Below the OS minimum,
dim further with a black overlay whose alpha rises. Transient indicator during edge drags,
fading ~600ms after release. Brightness continuous; font size stepped with
`HapticFeedbackConstants.CLOCK_TICK` per step. **A vertical drag in an edge strip must never
turn a page** — use a touch-slop directional lock committed on first significant movement.
Every zone size and every gesture's enable/disable is a setting, with a live interactive
zone-preview diagram in settings. RTL inverts page turns but *not* the brightness/font edges.

### 1.4 Dictionary
Fully offline, bundled. Tap/select a word → bottom sheet with headword, pronunciation, part
of speech, senses; the reading position must not move. **Inflections must resolve**
("running"→run, "mice"→mouse, "better"→good/well) — a lookup that fails on plurals is a
broken feature. Not-found offers a clearly-marked "search the web" `ACTION_VIEW` escape
hatch. **Every lookup is logged** (word, lemma, book, chapter, sentence) — this is a
feature, not telemetry; it feeds §1.6. Dictionaries pluggable behind one interface.

### 1.5 Highlighting
Selection toolbar: 5 colours, add note, copy, look up, share. A highlight carries a note and
any number of tags. **Anchoring is the hard problem.** Never anchor to pixels or page
numbers. Anchor to: resource href + progression + a text-quote selector (~32 chars of
prefix/suffix context). Resolve by position first, fall back to searching for the quote with
its context. **Unit-test the fallback path specifically.** Highlights render as coloured
backgrounds legible in both themes (tune each theme's tint separately, not one alpha). Tap a
highlight to edit/delete/re-colour.

### 1.6 Notes database
One searchable list across all books: highlights, notes, looked-up words. **SQLite FTS** over
highlight quotes and note bodies. Filter by book, colour, tag, date range, type. Tapping any
entry jumps to that exact spot. **Review mode**: flashcard queue over saved words and
highlights — prompt with the term blanked, reveal on tap, mark known / review again; spaced
intervals 1d/3d/7d/21d/60d (do *not* build SM-2). Daily due count. **Export**: Markdown (one
file per book, YAML frontmatter, blockquote + note + `#tags`, Obsidian-ready), CSV, JSON.
**Backup/restore**: single-file export of the whole database + settings, and an import that
restores it. Test the round-trip.

### 1.7 Theming
Light, Dark, AMOLED black, Sepia, plus follow-system. Applies to the app **and** the rendered
book content (inject colours into the rendering context — never invert, images look terrible
inverted). Optional warm-tint overlay with intensity slider, reader only. No flash of the
wrong theme; theme switches must not lose reading position.

---

## 2. Environment — verified, use exactly this

Assume nothing is installed. On macOS:

```bash
brew install openjdk@21            # NOT the Temurin cask: it needs an admin password
brew install --cask android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses > /dev/null
sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-36" \
  "build-tools;37.0.0" "emulator" "system-images;android-36;google_apis;arm64-v8a"
brew install gradle                # only to bootstrap: gradle wrapper --gradle-version 9.6.1
avdmanager create avd -n lumen_test -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7
```

`echo "sdk.dir=$ANDROID_HOME" > local.properties`.

**Versions that work together** (resolved against live Maven/Google metadata; re-check with
`curl` only if something fails to resolve):

```toml
agp = "9.3.1"          kotlin = "2.3.10"      ksp = "2.3.10"
compileSdk = "37"      minSdk = "26"          targetSdk = "37"
coreKtx = "1.19.0"     fragment = "1.8.9"     activityCompose = "1.13.0"
composeBom = "2026.06.01"                     lifecycle = "2.11.0"
navigationCompose = "2.9.8"                   datastore = "1.2.1"
hilt = "2.60.1"        hiltNavigationCompose = "1.4.0"
coroutines = "1.10.2"  room = "2.8.4"         coil = "3.5.0"
readium = "3.3.0"      desugarJdkLibs = "2.1.5"
junit = "4.13.2"       androidxTestExt = "1.3.0"  espresso = "3.7.0"  turbine = "1.2.1"
```

**Three build-file facts that each cost a failed build:**

1. **AGP 9 ships Kotlin itself and hard-errors if `org.jetbrains.kotlin.android` is also
   applied.** Do not add that plugin. Do apply `org.jetbrains.kotlin.plugin.compose`.
   `kotlin { compilerOptions { jvmTarget.set(JVM_21) } }` still works.
2. **Readium's AARs require core library desugaring**, or packaging fails outright:
   `isCoreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring(libs.desugar.jdk.libs)`.
   (Google's backport, GPLv2-with-Classpath-Exception, so safe to link.)
3. Room schema export: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, and add
   `sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")` so
   `MigrationTestHelper` can read them.

**Build times are the dominant constraint.** A cold full compile of this dependency set took
**over an hour** on an M-series Mac; incremental is ~10–60s. Plan to compile *once per
milestone*, not once per file.

---

## 3. Readium 3.3.0 — API cheat sheet

Artifacts: `org.readium.kotlin-toolkit:readium-{shared,streamer,navigator}:3.3.0` (BSD-3).
**Do not decompile the AARs to find these — they are correct as written.**

```kotlin
// Opening a publication
AssetRetriever(context.contentResolver, httpClient)          // httpClient: see note below
PublicationOpener(EpubParser(httpClient))                    // NOT DefaultPublicationParser:
    // that composite needs a PdfDocumentFactory you won't have until you do PDFs.
assetRetriever.retrieve(file): Try<Asset, RetrieveError>     // .getOrNull()
publicationOpener.open(asset, allowUserInteraction = false): Try<Publication, OpenError>
publication.metadata.title / .authors (List<Contributor>, .name) / .tableOfContents
publication.coverFitting(android.util.Size(600, 900)): Bitmap?   // suspend
publication.close()

// HttpClient is a single-method interface — implement a refusing one rather than using
// DefaultHttpClient, so a stray network attempt is a clear error, not a SecurityException:
interface HttpClient { suspend fun stream(HttpRequest): Try<HttpStreamResponse, HttpError> }
Try.failure(HttpError.Unreachable(DebugError("...")))

// Rendering
EpubNavigatorFactory(publication).createFragmentFactory(
    initialLocator = locator?,                 // null = start of book
    initialPreferences = EpubPreferences(...),
    configuration = EpubNavigatorFragment.Configuration().apply {
        selectionActionModeCallback = yourActionModeCallback   // replaces the system menu
    },
)
// then: fragmentManager.fragmentFactory = it; commitNow { replace(id, EpubNavigatorFragment::class.java, null, TAG) }

navigator.currentLocator: StateFlow<Locator>   // updates on page turn AND after repagination
navigator.go(link|locator, animated: Boolean)
navigator.submitPreferences(EpubPreferences)   // live — does NOT rebuild the navigator
navigator.addInputListener(InputListener)      // onTap(TapEvent), onDrag(DragEvent), onKey
navigator.currentSelection(): Selection?       // suspend; .locator, .rect
navigator.clearSelection()
(navigator as DecorableNavigator).applyDecorations(List<Decoration>, group: String)
DecorableNavigator.Listener { onDecorationActivated(OnActivatedEvent): Boolean }
Decoration(id, locator, Decoration.Style.Highlight(tint: Int, isActive: Boolean))

// EpubPreferences — 25 params, all optional, named:
EpubPreferences(
  fontFamily = FontFamily.SERIF | SANS_SERIF | OPEN_DYSLEXIC,   // value class
  fontSize = 1.0, lineHeight = 1.4, pageMargins = 1.0,          // multipliers, not px
  textAlign = TextAlign.JUSTIFY|START, hyphens = true,
  theme = Theme.LIGHT|DARK|SEPIA,                               // no AMOLED — see below
  backgroundColor = Color(argbInt), textColor = Color(argbInt),
  publisherStyles = false,                                      // MUST be false, see below
  scroll = false,
)

// Locator
locator.href / .title / .locations.{progression, position, totalProgression} / .text.{before, highlight, after}
locator.toJSON() ; Locator.fromJSON(JSONObject)
```

**Readium behaviours you must design around:**

- **`publisherStyles = false` is mandatory.** With it on, a book carrying its own CSS ignores
  the font size and line height the user just changed.
- **Readium's themes carry their own colours.** Pass `backgroundColor`/`textColor` explicitly
  or the page sits on pure black inside your `#121212` app. This is also the only way to get
  AMOLED, which Readium has no theme for.
- **Position restore is position-granular** (~1 position per 1024 chars). After a large
  font-size change you land on the same *position*, not the same line. Say so in the README
  rather than pretending otherwise.

---

## 4. The landmines — each of these cost real time once

1. **A Compose overlay above the navigator swallows every touch and silently stops pages
   turning.** No error, no crash — pages just stop. The navigator is a WebView.
   - For taps: use `navigator.addInputListener` (Readium filters link/selection taps for you).
   - For the gesture layer: wrap the `FragmentContainerView` in your **own `FrameLayout`
     subclass**, and use `onInterceptTouchEvent` to *watch* the stream and steal a gesture only
     once the directional lock says VERTICAL inside an edge strip. Everything else reaches the
     WebView untouched, so selection, long-press and swipe keep working. Re-anchor `startY` at
     the moment you intercept, or the first move jumps by the slop distance.
   - Two-finger tap: watch `dispatchTouchEvent` for `ACTION_POINTER_DOWN`.

2. **`MainActivity` must be a `FragmentActivity`** (Readium renders from a Fragment) **and
   must drop saved fragment state before `super.onCreate`**:
   ```kotlin
   savedInstanceState?.remove("android:support:fragments")
   ```
   The navigator can only be built by a factory holding an open `Publication` (async I/O), so
   after process death there is nothing to restore it with and the FragmentManager crashes.
   Discarding costs nothing — the locator in the database is the real source of truth. Still
   one activity, so single-activity architecture holds.

3. **Room FTS migrations: copy the DDL verbatim from Room's own exported schema JSON**
   (`app/schemas/<db>/N.json` → `createSql` and `contentSyncTriggers`). Room validates the
   live schema on open; one differing backtick fails at runtime with an unhelpful error.
   Use external-content FTS4 (`content=`) so Room generates the sync triggers, then
   `INSERT INTO x_fts(x_fts) VALUES('rebuild')` to populate from existing rows.

4. **Never pass raw user input to FTS `MATCH`.** Strip non-alphanumerics and wrap each term
   as `"term"*`. A stray apostrophe otherwise becomes a syntax error that surfaces as
   "no results" with no explanation.

5. **`org.json` is a stub on the JVM unit-test classpath** ("not mocked"). Put JSON assertions
   in `androidTest` rather than adding a third-party JSON library to test the platform's own.

6. **DataStore refuses two live instances over one file.** JUnit creates a new test-class
   instance per method, so give each test a unique file and cancel its scope in `@After`.

7. **Checkpoint the WAL before copying the database for a backup**
   (`PRAGMA wal_checkpoint(FULL)`), or the newest highlights are still in `-wal` and missing
   from the backup — exactly the data the backup exists for.

8. **Android refuses `OpenDocumentTree` access to the `Download` root** ("Use this folder" is
   disabled). Markdown export needs a subfolder. Not a bug; don't chase it.

9. **Kotlin string interpolation inside generated/patched code.** If you write source files
   through a script, verify the emitted Kotlin — a mis-escaped `$` produced a content
   description that literally read `Highlight ${color.displayName}` to screen readers and
   survived several milestones unnoticed.

10. **`am kill` only reaps a backgrounded process.** Send `KEYCODE_HOME` first, or you are
    testing nothing.

---

## 5. Data model — build this as schema v1, all of it

Do **not** grow the schema milestone by milestone; that cost five migrations and their tests
in the first build for no benefit. Define all of it up front, ship v1, and only write
migrations for changes made *after* the app has real data on the phone.

```
Book(id, contentHash UNIQUE, title, author, format, filePath, coverPath,
     addedAt, lastOpenedAt, progressFraction, currentLocatorJson)

Highlight(id, bookContentHash, colorId, createdAt, updatedAt,
          resourceHref, resourceProgression, locatorJson,
          quotePrefix, quoteExact, quoteSuffix, chapterTitle, progressFraction)

Note(id, highlightId?, bookContentHash, body, createdAt, updatedAt)
Tag(id, name UNIQUE)  +  HighlightTag(highlightId, tagId)

LookupEvent(id, word, lemma, bookContentHash?, resourceHref?, chapterTitle?,
            sentence?, dictionaryId, found, createdAt)

ReviewItem(id, sourceType, sourceId, intervalIndex, dueAt, lastResult, createdAt)
            UNIQUE(sourceType, sourceId)     -- makes seeding idempotent

HighlightFts  @Fts4(contentEntity = Highlight)  over quoteExact
NoteFts       @Fts4(contentEntity = Note)       over body
```

**The one non-obvious decision, and it matters:** highlights reference the book's
**content hash, not its row id, with no foreign key**. Three requirements then fall out for
free instead of needing special cases — deleting a book can leave its highlights behind,
re-importing the same file silently reunites them, and "the same book" survives a rename.
Cascades are done explicitly in repository code, where they are visible.

Highlights are the durable asset. Any migration touching them ships with a test that
populates the old schema, migrates, and asserts every highlight still resolves.

---

## 6. How to work — this is a requirement, not a style note

The first build of this app burned most of its context on four things. Avoid all four.

### 6.1 Verify with instrumented tests, not by driving the UI
This is the single biggest saver. Driving the app through `adb shell input tap` +
`uiautomator dump` means hunting coordinates, re-dumping after every keyboard appearance, and
re-reading screenshots — and it is *fragile*, so you do it repeatedly.

Instead, assert against the real database and real components:

```bash
adb shell "run-as <pkg> sqlite3 /data/data/<pkg>/databases/lumen.db 'SELECT ...'"
```

Write instrumented tests for anything with a "done when": the dictionary resolving three
words offline, the backup round-trip, a highlight surviving a font change, the review
schedule. These are repeatable, they stay in the suite, and they cost a fraction of the
context of a UI-driving session. Reach for `uiautomator` only when the thing being proven
*is* the pixels.

### 6.2 Take at most 2–3 screenshots per milestone
Images are expensive to read. Screenshot the finished surface, not each step. To prove a
highlight rendered, **pixel-scan the framebuffer for the tint** rather than reading the image:

```python
hits = [(x,y) for y in range(140,h-120,3) for x in range(0,w,3)
        if (lambda p: p[0]>70 and p[1]>55 and p[2]<80 and p[0]-p[2]>45)(im.getpixel((x,y)))]
```

### 6.3 Never poll a build
Do not `tail` a log in a loop. Run the build in the background and block once:

```bash
./gradlew test connectedAndroidTest lint --console=plain > /tmp/build.log 2>&1; echo "exit=$?"
```

Batch every Gradle task you need into **one** invocation. Two concurrent invocations
contend on the project lock and stall.

### 6.4 Write whole files, compile once
Write all of a milestone's files, then compile. Do not compile after each file. When a
compile fails, fix every reported error before recompiling.

### 6.5 Other rules of engagement
- **Ask before adding any dependency** not listed in §2. Say what it does, its licence, its
  size. (Note `desugar_jdk_libs` is already forced by Readium — not a choice.)
- **Never silently stub.** A thing that can't work yet shows a visible "not built yet" state.
- Keep `DECISIONS.md` (one dated entry per non-obvious choice, with the rejected alternative)
  and `PROGRESS.md` (milestone status + what you actually verified).
- `git init` day one; conventional commits; `.gitignore` the keystore *before* it exists.
- **Report honestly.** If a test fails or a feature half-works, say so plainly.
- If a requirement here is wrong or self-contradictory, say so and propose the alternative
  rather than quietly reinterpreting it.

---

## 7. Milestones

Five, not ten — each ends with an installable app and a stated verification. Batch the work
inside each one; compile and test once at the end.

- **M1 — Skeleton + Library.** Scaffold, Hilt, Compose, M3, nav, four themes persisted via
  DataStore, **the complete schema from §5**, SAF import with content hashing (copy+hash in a
  single streaming pass into a staging file, parse from staging, rename into place only on
  success), Readium metadata + cover extraction, grid, sort/search, delete-with-highlight-prompt.
  *Verified when:* three real EPUBs import with correct covers, titles and authors; all four
  themes persist across `am kill`.

- **M2 — Reader + gestures.** Readium navigator hosted per §4.1–4.2, paginated, TOC, progress
  persistence, typography, book-content theming, and the **full §1.3 gesture layer** including
  the settings zone-preview diagram.
  *Verified when:* read → `am kill` → reopen lands on the same page; font size change keeps the
  place; a full-height drag in the left edge strip leaves the locator byte-identical (**the
  page did not turn**); tap zones turn pages; brightness and font-size values persist.

- **M3 — Highlighting.** Selection via `selectionActionModeCallback`, five colours, the
  three-part anchor, decoration rendering, tap-to-edit, notes, tags.
  *Verified when:* a highlight made at 80% font still renders on the right words at 300% after
  a process kill — **and on the correct occurrence of a repeated word** — with passing unit
  tests for the quote-fallback path.

- **M4 — Dictionary.** Build the asset with a script from WordNet 3.1 + CMUdict (see below),
  Morphy lemmatisation, lookup sheet, lookup logging, web escape hatch, pluggable interface.
  *Verified when:* "running", "mice" and "better" all resolve **in airplane mode**, as an
  instrumented test against the real bundled asset.

- **M5 — Notes, review, ship.** Cross-book FTS list with filters and jump-to-spot; review
  queue with the 1/3/7/21/60 ladder and due count; Markdown/CSV/JSON export; backup/restore;
  then release signing, R8, README.
  *Verified when:* search a phrase → jump to it → export Obsidian-ready Markdown → back up →
  `pm clear` → restore → find it again, with FTS still working. Then the same on a **release**
  build, since R8 breakage only shows there.

### The dictionary asset
WordNet 3.1 (`https://wordnetcode.princeton.edu/wn3.1.dict.tar.gz`, permissive licence) has
the senses *and* the irregular-form exception lists Morphy needs (`noun.exc` has `mice mouse`,
`adj.exc` has `better good well`). **It has no pronunciations** — merge CMUdict 0.7b
(`https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict`, public domain) and
convert ARPAbet→IPA at build time.

Write `tools/build_dictionary.py` producing one SQLite file: `synsets(id,pos,definition,
examples)` ← `senses(lemma,pos,sense_num,synset_id)` (normalised, so a shared synset is stored
once — this is most of the size saving), plus `exceptions(inflected,base,pos)`,
`pronunciations(lemma,ipa)`, `meta`. Restrict pronunciations to lemmas WordNet knows.
Result: ~147k lemmas, ~207k senses, **~20MB**, APK ~39MB. **Gitignore the `.db`** and
regenerate it; do not commit 20MB of binary.

Ship it in `assets/` and copy it out to `filesDir` on first use (an asset inside the APK is
compressed and SQLite cannot open it in place). Write to a temp name and rename, so an
interrupted copy cannot leave a truncated database that looks installed.

**Morphy needs one rule WordNet does not have:** undouble the consonant after `-ing`/`-ed`
detachment, or "running"→"runn" and "stopped"→"stopp" and the verb is unreachable. Guard it
so real words ending in a double letter ("pass", "bell") survive.

---

## 8. Quality bar

- Cold start to library < 1s on a real phone. Page turn: no dropped frames.
- No ANR importing a 40MB EPUB — parse off the main thread with visible progress.
- Rotation, process death and multi-window must not lose reading position or an in-progress
  note. Test process death explicitly.
- Accessibility: content descriptions on all controls; page-turn buttons in the chrome as a
  non-gesture alternative; app UI respects system font scale (the *book* uses my chosen size).
- **Non-negotiable tests**: highlight anchoring/resolution incl. the quote fallback, the
  lemmatizer, progress/schedule maths, and export formatting. Broader coverage welcome.
- Zero declared permissions. The dictionary is bundled and the web escape hatch is an
  `ACTION_VIEW` intent, so `INTERNET` is genuinely unnecessary — keep it that way.
- No analytics, no crash SDK, no ads, no accounts. Ever.

---

## 9. Ship

Signed release APK, sideloaded. **Do not publish anywhere.**

1. Give me the exact `keytool` command; tell me where to keep the keystore and why losing it
   means I can never upgrade in place. Do not generate it or ask for my password. Wire the
   build to read `keystore.properties` (gitignored) or env vars, and **fail loudly** if absent.
2. `minifyEnabled true`, `shrinkResources true`, R8 rules keeping Readium/Room reflection
   working — then **actually open a book on the release build**, because that is where R8
   breakage appears.
3. Tell me the APK path and size, and both install routes (`adb install -r`, and copying to
   the phone with the "install unknown apps" note).
4. `README.md`: what it is, how to build/sign/install, how to back up, where my data lives on
   disk, known limitations, and a privacy note stating exactly which permissions are declared
   (none) and that nothing leaves the device.

---

## 10. Before writing code

Reply in under 15 lines with: your package/module layout; anything in §2–§5 you believe is
wrong; and your plan for the first milestone. Then wait for my go-ahead.
