# HiraLearn

A native Android app (Kotlin, Jetpack Compose) that teaches Japanese hiragana: reading, writing **and** hearing them.
It works fully offline. The data model is built so that optional online sync can be added later.

## How learning works

**Learn** picks the next unfinished lesson (あいうえお, かきくけこ … dakuten … yōon) and teaches up to 5 new kana.
Each kana is introduced (big glyph, reading, its sound, animated stroke order) and then tested several times, with longer and
longer gaps, mixed in with the other new kana:

1. pick the reading of the kana
2. pick the kana for a reading
3. trace the kana over a faint guide
4. hear the kana and pick it
5. type the reading
6. write the kana from memory

When all its steps are done, the kana is *learned*. It gets three cards, **reading**, **writing** and **listening**,
each with its own schedule.

**Review** asks the cards that are due. The question type gets harder as a card gets stronger (multiple choice → typing;
picking the kana → drawing it; picking what you hear → writing what you hear).

**Practice** is offered when nothing is due ("Practice anyway"). It asks learned cards that aren't due yet,
weakest first, mixing all question types (choice, typing, drawing) at any level. Right answers don't change the schedule, since answering early proves less than remembering after
the full gap. Mistakes and mix-ups still bring kana back sooner, exactly as in a review.

In **Settings → Question types** each type (multiple choice, typing, drawing) can be switched on or off for reviews
and practice. Reading always keeps choice or typing, and writing always keeps choice or drawing.

Spaced repetition only works if you come back, so **Settings → Daily reminder** can send a notification at a time
you choose ("7 kana are waiting") on days when reviews are due. It is off until you turn it on, and nothing is sent
when you are all caught up.

Sound comes from the phone's Japanese text-to-speech, so it adds nothing to the app's size. If the phone has no
Japanese voice, listening is switched off (Settings offers to install one) and listening cards are neither asked nor
counted as due. Kana learned before listening existed, or while it was off, get their listening card as soon as
listening works, a little below their reading level. Sound-alikes (じ/ぢ, ず/づ, お/を) are only asked by choice,
since the sound alone can't say which one to write.

Scheduling (`core/Scheduler.kt`) works like Memrise:

| Answer | Effect |
| --- | --- |
| correct | level +1, next review after 4 h → 12 h → 1 d → 3 d → 7 d → 14 d → 30 d → 60 d → 120 d → 240 d |
| wrong / "I don't know" | level −2, due again in 10 minutes, **and** asked again a few questions later in the same session |
| mixed up with another kana (picked お for あ, typed "chi" for さ, drew a kana that matches another one) | the **other** kana's card is also marked not known (level −2, due in 10 min) and is asked again in this session |

Multiple-choice distractors are chosen from kana that look alike (あ/お/め/ぬ, さ/ち/き, ね/れ/わ, は/ほ/け, ば/ぱ …),
so learners have to really tell them apart. Options never share a reading (じ/ぢ, ず/づ, お/を).

### Handwriting

Drawn kana are graded automatically against KanjiVG reference strokes (`core/Handwriting.kt`). The drawing is
centred and scaled, then each stroke is compared with the matching reference stroke. This catches:

- a wrong shape,
- a wrong stroke count,
- strokes in the wrong order or direction,
- writing a different kana (counted as a mix-up).

The learner can overrule the grader ("I was right"), or switch drawing off for the session ("Can't draw right now")
or for good in Settings.

## Project layout

```
core/   Pure Kotlin/JVM, no Android: kana catalogue, scheduler, session engine, handwriting grader. Unit-tested.
app/    Android app: Compose UI, Room database, ViewModels.
tools/  kvg2txt.py: converts KanjiVG SVGs into core/src/main/resources/hiragana_strokes.txt
```

Keeping all learning logic in `core` means it is tested on the JVM, and can be reused by a sync server later.

## Offline first, sync later

All progress is stored locally in Room (`app/.../data/Database.kt`):

- `cards`: the current state per (kana, skill), with `updatedAt` for last-writer-wins conflict resolution.
- `review_events`: an append-only log of every answer with UUID ids and a `syncedAt` column, so the logs of several
  devices can be merged without conflicts, and card states can be rebuilt from the log if needed.
- Kana ids (`ka`, `shi`, `di` …) are stable and never change.

Adding sync then means writing a `SyncRepository` that uploads events where `syncedAt IS NULL`, downloads remote
events and cards, and merges them. The UI and session engine don't need to change. Room schemas are exported to
`app/schemas/` so database migrations can be tested.

## Building

Requirements: JDK 17+ and the Android SDK (compile SDK 37). The easiest way is to open the project in Android Studio.

```
./gradlew :core:test          # learning logic unit tests
./gradlew :app:assembleDebug  # build the APK
./gradlew :app:installDebug   # install on a connected device / emulator
```

## Releasing

CI (`.github/workflows/ci.yml`) checks every pull request and every commit on `master`. A release is a version tag:

1. Set `versionName` in `app/build.gradle.kts` (e.g. `0.2.0`) and merge that to `master`.
2. Tag the merged commit and push the tag: `git tag v0.2.0 && git push origin v0.2.0`.
3. `.github/workflows/release.yml` checks that the tag is on `master` and matches `versionName`, sets `versionCode` to
   the workflow's run number, builds and signs the bundle, and creates a GitHub Release with the `.aab`, the R8
   mapping and release notes.
4. Download the `.aab` from the GitHub Release and upload it in Play Console.

The release notes come from the commit messages since the previous tag, which follow
[Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `perf:`, `refactor:`, `docs:`, and
`build:`, `chore:`, `ci:`, `style:`, `test:` for changes that stay out of the notes; `!` marks a breaking change).
[git-cliff](https://git-cliff.org) groups them as set in `cliff.toml`. To preview the next release's notes, run
`git cliff --unreleased --strip all`. With squash merges, the PR title becomes the commit message, so give PRs
conventional titles. The Play Console "What's new" text is separate: write it by hand for users, in every language.

One-time GitHub setup:

- **Environment** (Settings → Environments → New environment): name it `release`. Under *Deployment branches and
  tags*, choose *Selected branches and tags* and add the tag pattern `v*`. Add these environment secrets:
  - `HIRALEARN_KEYSTORE_BASE64`: the upload keystore as one base64 line. In PowerShell:
    `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) | Set-Clipboard`
  - `HIRALEARN_STORE_PASSWORD`, `HIRALEARN_KEY_ALIAS`, `HIRALEARN_KEY_PASSWORD`: as in `keystore.properties`.
- **Tag ruleset** (Settings → Rules → Rulesets → New tag ruleset): target `v*`, enable *Restrict updates* and *Restrict
  deletions*, so a released tag can never be moved to other code.
- If the release workflow is ever deleted and recreated, its run number starts again at 1. Then set the repository
  variable `VERSION_CODE_OFFSET` (Settings → Secrets and variables → Actions → Variables) to the last versionCode in
  Play.

## Languages

The app is in English (default), Hungarian and German. It follows the phone language; on Android 13+ the language can
also be chosen in Settings or in the system's per-app language settings.

## Credits

Stroke data: [KanjiVG](https://kanjivg.tagaini.net) © Ulrich Apel, CC BY-SA 3.0. The derived file
`core/src/main/resources/hiragana_strokes.txt` is under the same licence.
Fonts: Klee One (Fontworks), Noto Serif JP and Noto Serif (Google), SIL Open Font License 1.1 (see `licenses/`).
