# AGENTS.md

Guidance for AI coding agents (and humans) working on HiraLearn. See `README.md` for what the app does.

## Project

Native Android app teaching Japanese hiragana (reading and writing) with Memrise-style spaced repetition.
Offline first; optional online sync is planned, so keep the data model sync-friendly (see below).

- `core/`: pure Kotlin/JVM library, **no Android dependencies**. Kana catalogue (`Kana.kt`), scheduler
  (`Scheduler.kt`), session engine (`StudySession.kt`), handwriting grader (`Handwriting.kt`), stroke data
  (`src/main/resources/hiragana_strokes.txt`).
- `app/`: Android app. Jetpack Compose + Material 3 UI, Room persistence, ViewModels, manual DI (`AppContainer` in
  `HiraLearnApplication.kt`), navigation-compose with string routes (`MainActivity.kt`).
- `tools/kvg2txt.py`: regenerates the stroke data from KanjiVG SVGs.

## Commands

Requires JDK 17+ and the Android SDK (`ANDROID_HOME` or `local.properties` with `sdk.dir=...`).

```
./gradlew :core:test            # fast JVM tests for all learning logic; run after any core change
./gradlew :core:pitest          # mutation testing of core (~1 min); report in core/build/reports/pitest/
./gradlew :app:assembleDebug    # build the app (APK in app/build/outputs/apk/debug/)
./gradlew :app:installDebug     # install on a connected device/emulator
./gradlew :app:assembleRelease  # R8-minified build; run when touching dependencies or resources
./gradlew :app:bundleRelease    # signed .aab for Google Play (app/build/outputs/bundle/release/)
```

Release signing uses the Play *upload key*. Put a `keystore.properties` in the project root (gitignored, as are
`*.jks`/`*.keystore`) with `storeFile` (path relative to the root), `storePassword`, `keyAlias` and `keyPassword`, or
set `HIRALEARN_STORE_FILE`, `HIRALEARN_STORE_PASSWORD`, `HIRALEARN_KEY_ALIAS` and `HIRALEARN_KEY_PASSWORD`. Without
either, release builds come out unsigned. Don't bump `versionCode` by hand: the release workflow sets it from its run
number. A release is a `v<versionName>` tag on `master` (see README → Releasing).

Backup (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`) includes only `hiralearn.db` (with WAL files) and the
`settings` prefs. A new database file or prefs file that holds user data must be added to both.

Always run `:core:test` and `:app:assembleDebug` before calling a change done.
After changing learning logic in `core`, also run `:core:pitest`: it fails below a 90% mutation kill rate. If a new
mutant survives, add a test that pins the behaviour (see `MutationGapTest.kt`) rather than lowering the threshold.

## Rules

- **Learning logic belongs in `core`**, with unit tests in `core/src/test`. The app only displays state and persists
  `SessionUpdate`s. Don't put scheduling or grading decisions in ViewModels or composables.
- **Kana ids are permanent** (`ka`, `shi`, `di`, `du`, …). They are database keys and future sync keys. Add new
  kana with new ids; never rename or reuse existing ones.
- **Word ids are permanent** too (`Words.kt`). A new word needs only catalogue kana (no っ or ー), a `word_<id>` string in
  every language, and an entry in `wordMeanings` (`ui/Strings.kt`).
- **Core behaviour the user asked for (keep it):**
  - a new kana is introduced first, then tested;
  - a right answer pushes the next review further out;
  - a wrong answer brings it back sooner and re-asks it later in the same session;
  - a mix-up with another kana also marks *that* kana as not known and asks it again soon;
  - reading, writing and listening are separate cards;
  - Practice (nothing due) never pushes a card further out, but its mistakes and mix-ups do reset cards.
- **Database changes**: bump the `@Database` version, add a Room migration (never destructive fallback, since user
  progress must survive), and commit the exported schema in `app/schemas/`.
- **Sync readiness**: `review_events` is append-only with UUID ids and `syncedAt`, and `cards.updatedAt` is set on
  every change. Keep it that way.
- Multiple-choice options must never share a reading with the answer (`Kana.soundsLike`).
- **Translations**: every user-visible string lives in `app/src/main/res/values/strings.xml` (English, default) and
  must also be added to `values-hu` (Hungarian) and `values-de` (German). Use `stringResource` /
  `pluralStringResource`; never hardcode text in Compose. Lesson and group names are translated by index via
  `R.array.lesson_titles` / `R.array.group_titles` (`ui/Strings.kt`), so keep those arrays in `Hiragana.lessons` order.
  German durations follow "in", so plural forms use the dative ("Tagen").
- The bundled fonts are subsets: if a translation needs a new character, re-subset (see `tools/`). Noto Serif JP lacks
  ő/ű, which `tools/add_glyphs.py` copies in from Noto Serif.
- Stroke data is KanjiVG, CC BY-SA 3.0. Keep the attribution (file header, README, Settings → About → Licences).
- Everything bundled must be credited in the Licences screen (`ui/settings/LicensesScreen.kt`, texts in `res/raw`):
  a new library goes into `library_names` (and needs its licence text if it isn't Apache 2.0), a new font into
  `font_copyrights`.

## Design: "Ink & Seal"

The app looks like a calligraphy desk. Keep new UI in this style; the building blocks are in `app/.../ui/ink/`
(`Ink.kt`, `Brush.kt`) and the palette in `ui/theme/Theme.kt` (`LocalInkColors`, `LocalFeedbackColors`).

- **Paper**: every screen draws `Modifier.washiPaper()` and uses transparent Scaffold/TopAppBar containers. The theme
  provides `onSurface` as the default text colour, so text stays readable in night-ink (dark) mode.
- **Colour meaning**: sumi ink for content, indigo (`seal`) for the app colour and "strong/done" seals, matcha green
  for right / all caught up, vermilion **only** for wrong / not known. Never use red for decoration.
- **Progress = ink**: a kana goes faint outline (new) → pencil (learning) → ink (familiar) → full ink + OK seal
  (strong), via `inkLevel()` / `InkedKana`. Don't reintroduce coloured tiles.
- **Shapes**: no plain Material cards. Use `inkWash()` blobs for panels, `InkButton` (elliptic ink blot) for
  buttons, `ManuscriptSquare` for kana cells, `PaperOption` for answer choices, `Seal` for stamps.
- **Seals and marks contain no kanji** (the app teaches hiragana only): seals say "OK"; right/wrong are a brushed
  ○ / × (`BrushMark`).
- **Ink strokes** go through `brushPath()` (HAND = speed-based width for the learner, REFERENCE = calligraphic
  profile for model strokes).
- **Fonts**: `KanaFont` (Klee One, textbook handwriting) for every displayed kana, `SerifFont` (Noto Serif JP) for
  text. Both are subset to hiragana + Latin; re-run the subsetting if new characters are needed.

## Build setup gotchas

- **AGP 9 has built-in Kotlin**: don't apply `org.jetbrains.kotlin.android` in `app`. The root build declares
  `kotlin.jvm` (used by `core`) so all modules share one Kotlin Gradle plugin version.
- **compileSdk is 37.2**, set via `compileSdk { version = release(37) { minorApiLevel = 2 } }`, because
  Compose 1.13 requires API 37.1+. The SDK package is `platforms/android-37.2`
  (install with `cmdline-tools/latest/bin/android sdk install "platforms/android-37.2"`).
- Versions live in `gradle/libs.versions.toml`. Check the newest stable versions on Google Maven / Maven Central
  before bumping. AndroidX AARs declare a `minCompileSdk` that may force a compileSdk bump.

## Code style

Kotlin official style (`kotlin.code.style=official`, see `.editorconfig`), 4-space indent, max line ~140.
Keep comments for the *why*.
