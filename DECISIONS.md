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

## 2026-08-01 — Atkinson Hyperlegible as the dyslexia-friendly face
Rejected OpenDyslexic: licensing history is muddier and its rendering at small sizes is
worse. Atkinson Hyperlegible is SIL OFL 1.1, designed by the Braille Institute for low
vision, and disambiguates the same character pairs.
