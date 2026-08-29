# Recall

A small, good-looking flashcard app for Android — Anki's idea (spaced repetition), with a much
simpler surface. Cards live in decks, and an answer can be **text**, a **link**, or an **image**.

Built with Kotlin + Jetpack Compose + Room.

---

## Table of contents

1. [What the app does](#what-the-app-does)
2. [Running it on your phone](#running-it-on-your-phone)
3. [How an Android project maps to what you already know](#how-an-android-project-maps-to-what-you-already-know)
4. [Project layout](#project-layout)
5. [How the pieces fit together](#how-the-pieces-fit-together)
6. [The Kotlin you need to read this code](#the-kotlin-you-need-to-read-this-code)
7. [The scheduling algorithm](#the-scheduling-algorithm)
8. [Things you might want to change first](#things-you-might-want-to-change-first)

---

## What the app does

**Deck list (home).** A banner showing how many cards are due across all decks, then one row per
deck with its own accent colour, card count, and a progress bar. Tap a deck with cards due to jump
straight into reviewing; tap one with nothing due to browse it.

**Add card.** The screen the whole app is built around, kept to four things: pick a deck, type the
question, pick the answer type, fill it in. Save is disabled until the card is actually valid.

- **Text** — a normal multi-line answer. Reflows to the screen width.
- **Code** — monospace, literal whitespace, scrolls sideways instead of wrapping.
- **Link** — a URL. During review it renders as a tappable chip that opens your browser.
- **Image** — opens Android's system photo picker (no storage permission needed).
- **Audio** — opens the document picker filtered to audio. Plays inline with a scrubbing progress
  bar, and stops when you background the app.

Image and audio files are *copied* into the app's private folder, so a card keeps working even if
you later delete the original from your gallery.

### Why Code is its own type, and not just Text

Prose is rendered in a proportional font and reflowed to fit the screen. Both of those quietly
destroy code: proportional glyphs break column alignment, and reflowing discards the indentation
that carries the structure. A snippet pasted into a Text answer comes out unreadable — and an answer
you cannot read is a card that cannot teach you anything.

This is not a preference; it is what every tool that displays code does. Anki stores fields as HTML
and its users wrap snippets in `<pre><code>` with a syntax-highlighting add-on — even there, code is
a distinct rendering mode, just reached through a more awkward door. Recall makes it a first-class
answer type instead.

Deliberately *not* included: syntax highlighting. It needs a lexer per language and a theme that
works in light and dark, and it buys much less than monospace-plus-indentation does. Add it when you
actually miss it.

An optional **note** field is tucked behind a "Add a note" tap so the default form stays short.

**Deck detail.** Every card in the deck, each tagged with its answer type and when it's next due.
Tap a card to expand it and see the answer. Delete cards or the whole deck from here.

**Review.** Question first, tap to flip, then grade yourself with four buttons — **Again / Hard /
Good / Easy** — each labelled with when you'd next see the card. That grade feeds the scheduler.
Cards you mark "Again" come back later in the same session.

**Settings.** One setting: a daily reminder. Pick a time, and once a day the app checks whether
anything is actually due and notifies you only if it is — a reminder that fires when there is nothing
to do is a reminder you learn to swipe away.

The app is offline-only. Nothing leaves the phone, there are no accounts, and no analytics. The only
permission it declares is notifications, and only for the reminder; the photo and audio pickers
grant access to the single file you choose without any storage permission at all.

---

## Running it on your phone

You need **Android Studio** (the free official IDE — it bundles its own JDK and downloads the
Android SDK for you). Download it from <https://developer.android.com/studio>.

### 1. Open the project

Android Studio → **Open** → select this `Recall` folder → wait for "Gradle sync" to finish in the
status bar. The first sync downloads Gradle and all the libraries, so it takes a few minutes.

If it complains about a missing SDK, it will offer to install it — accept.

### 2. Put your phone in developer mode

On the phone: **Settings → About phone → tap "Build number" seven times**. Then
**Settings → System → Developer options → enable "USB debugging"**.

### 3. Plug it in and run

Connect by USB, tap **Allow** on the "Allow USB debugging?" prompt, then in Android Studio pick your
phone from the device dropdown in the toolbar and press the green **▶ Run** button. The app is
installed and launched.

### Or build an APK from the command line

```bash
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and tap it (you'll
have to allow "install from unknown sources" for whichever app you opened it with).

**On this machine that already works.** The Android SDK is installed at `~/Library/Android/sdk`,
and `local.properties` at the project root already points Gradle at it. The only thing the shell
needs is a JDK on `JAVA_HOME`:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew assembleDebug
```

> On a fresh machine you'd need `JAVA_HOME` pointing at a JDK 17 or newer and either `ANDROID_HOME`
> pointing at the SDK, or a `local.properties` file containing `sdk.dir=/path/to/Android/sdk`.
> `local.properties` is machine-specific and deliberately gitignored — never commit it. Running from
> inside Android Studio needs none of this.

### Install straight to the phone over USB

With the phone plugged in and USB debugging on:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## How an Android project maps to what you already know

| Java/backend world | Here |
|---|---|
| Maven `pom.xml` | `build.gradle.kts` (one at the root, one per module in `app/`) |
| Multi-module parent POM | root `build.gradle.kts` + `settings.gradle.kts` |
| `mvn package` | `./gradlew assembleDebug` |
| `src/main/java` | `app/src/main/java` (Kotlin lives here too) |
| `src/main/resources` | `app/src/main/res` (but compiled and type-checked, not just copied) |
| `web.xml` / deployment descriptor | `AndroidManifest.xml` |
| JPA entity / Hibernate | Room `@Entity` + `@Dao` (`data/` package) |
| Repository / service layer | `RecallRepository` |
| Controller / presenter | `RecallViewModel` |
| JSP / Swing / template | Compose `@Composable` functions (`ui/screens`) |
| Lombok / MapStruct annotation processing | KSP (`ksp(...)` in `app/build.gradle.kts`) |

Two things have no clean Java analogue and are worth knowing up front:

**`res/` is code.** Files under `app/src/main/res` are compiled into a generated `R` class, so
`@string/app_name` in XML and `R.string.app_name` in Kotlin are checked at build time.

**One Activity, many screens.** An `Activity` is roughly "a window". This app has exactly one
([`MainActivity.kt`](app/src/main/java/com/recall/app/MainActivity.kt)); everything else is Compose
functions that a `NavHost` swaps in and out, like a card layout.

---

## Project layout

```
Recall/
├── build.gradle.kts            # plugin versions for the whole build
├── settings.gradle.kts         # which modules exist, where to fetch libraries
├── gradle/wrapper/             # pins the Gradle version; ./gradlew uses this
└── app/
    ├── build.gradle.kts        # SDK levels, compile options, dependencies
    └── src/main/
        ├── AndroidManifest.xml # app name, icon, launcher activity
        ├── res/                # strings, colours, launcher icon
        └── java/com/recall/app/
            ├── MainActivity.kt         # entry point + navigation graph
            ├── RecallApp.kt            # Application subclass (a place for setup)
            ├── data/                   # storage layer
            │   ├── Card.kt             # @Entity — one flashcard
            │   ├── Deck.kt             # @Entity — a named group of cards
            │   ├── AnswerType.kt       # TEXT / LINK / IMAGE
            │   ├── Converters.kt       # teaches Room to store the enum
            │   ├── DeckWithCounts.kt   # deck + card counts, for the home screen
            │   ├── RecallDao.kt        # the SQL
            │   ├── RecallDatabase.kt   # Room database singleton
            │   ├── MediaStore.kt       # copies picked images/audio into private storage
            │   ├── Migrations.kt       # how to change the schema without losing data
            │   ├── ReminderPrefs.kt    # the three reminder settings
            │   └── RecallRepository.kt # the only thing the UI talks to
            ├── reminder/                # the daily notification
            │   ├── ReminderScheduler.kt # works out when it next fires
            │   ├── ReminderWorker.kt    # runs in the background, posts the notification
            │   └── Notifications.kt     # the notification channel
            ├── srs/                    # spaced-repetition scheduling
            │   ├── Sm2.kt              # the SM-2 algorithm + Rating enum
            │   └── DueFormat.kt        # "in 3 days", "due now"
            └── ui/
                ├── RecallViewModel.kt  # app state, survives rotation
                ├── theme/              # colours, typography, light/dark schemes
                ├── components/
                │   ├── AnswerView.kt   # renders each of the five answer types
                │   └── AudioPlayer.kt  # MediaPlayer wrapped for Compose
                └── screens/
                    ├── DeckListScreen.kt
                    ├── DeckDetailScreen.kt
                    ├── AddCardScreen.kt
                    ├── ReviewScreen.kt
                    └── SettingsScreen.kt
```

---

## How the pieces fit together

```
Compose screens  ──user taps──▶  RecallViewModel  ──▶  RecallRepository  ──▶  Room DAO  ──▶  SQLite
      ▲                                                                                        │
      └──────────────  Flow<List<Card>> re-emits automatically on any write  ◀─────────────────┘
```

The important consequence: **nothing manually refreshes the UI.** Room's `Flow` return types make
every query a live subscription. Insert a card and the deck list's counts update on their own,
because that query's result changed. There is no `notifyDataSetChanged`, no observer wiring to
forget.

Compose works the same way in miniature. A `@Composable` function is a *description* of the UI for
the current state, not a widget tree you mutate. When state it reads changes, Compose re-runs that
function and redraws what differs. So instead of `button.setEnabled(false)` you write
`enabled = canSave` and let `canSave` change.

**Where each responsibility lives:**

- **`data/`** knows how things are stored and nothing about how they look.
- **`srs/`** is pure logic — no Android imports at all, which is why it would be the easiest part to
  unit test.
- **`ui/RecallViewModel.kt`** holds state and calls the repository. It survives screen rotation,
  which is the main reason it exists.
- **`ui/screens/`** only draws and reports taps upward through callbacks (`onSave`, `onRate`). None
  of the screens touch the database, so you can read any one of them on its own.

---

## The Kotlin you need to read this code

You'll recognise most of it. The handful of things that look alien:

```kotlin
val x = 1              // final variable
var y = 2              // mutable variable
val name: String       // never null — the compiler enforces it
val note: String?      // may be null; you must handle that before using it
note?.length           // null-safe call: null if note is null
```

**`data class`** — a class with `equals`/`hashCode`/`toString`/`copy` generated:

```kotlin
data class Card(val id: Long, val question: String)
val updated = card.copy(question = "new text")   // new instance, one field changed
```

**`object`** — a singleton. `object Sm2 { fun apply(...) }` is a class with only static methods.

**Trailing lambdas.** If the last argument is a function, it moves outside the parentheses. So
`Button(onClick = { ... }) { Text("Save") }` is passing two lambdas — the second is the button's
content.

**`suspend` and coroutines.** `suspend fun` can only be called from a coroutine, and may pause
without blocking a thread. `viewModelScope.launch { ... }` starts one that is automatically
cancelled when the screen goes away — a managed background task rather than a raw `Thread`. Room
requires database calls to be `suspend` precisely so they can't run on the UI thread.

**`Flow<T>`** — a stream you subscribe to. Roughly a `Publisher` from Reactive Streams.

**`by` (delegation).** `var name by remember { mutableStateOf("") }` means "store this in a
Compose state holder, but let me read and write it like a plain variable". The `remember` part means
"keep this value across redraws".

**`@Composable`** marks a function that draws UI. It can only be called from another `@Composable`.

---

## The scheduling algorithm

[`srs/Sm2.kt`](app/src/main/java/com/recall/app/srs/Sm2.kt) — about twenty real lines. Each card
carries:

- **`intervalDays`** — how long until you see it next
- **`easeFactor`** — how easy this card has been for you (starts at 2.5, floors at 1.3)
- **`repetition`** — how many times in a row you've recalled it
- **`dueAt`** — a timestamp; `dueAt <= now` means the card is in the review queue

Grade a card and:

- **Again** → interval resets to 1 day, ease drops by 0.20, lapse counted.
- **Hard / Good / Easy** → first success is 1 day (3 for Easy), second is 4 days (6 for Easy), and
  after that `interval × easeFactor`. Ease itself nudges up for Easy and down for Hard.

So a card you keep getting right goes 1 → 4 → 10 → 25 → 60 days, while one you keep failing stays in
your face. That growth curve is the whole point of spaced repetition: you review right before you'd
have forgotten, which is exactly when the recall effort does the most good.

The queries that make it work are in [`RecallDao.kt`](app/src/main/java/com/recall/app/data/RecallDao.kt) —
"due" is just `WHERE dueAt <= :now`.

---

## Things you might want to change first

Each of these is a small, self-contained edit — good for getting your bearings.

**Change the colours.** [`ui/theme/Color.kt`](app/src/main/java/com/recall/app/ui/theme/Color.kt).
`DeckAccents` is the list decks cycle through; add a colour and it shows up in the new-deck dialog
automatically.

**Change how aggressive the scheduling is.** The numbers in `Sm2.apply`. Making the first interval
3 days instead of 1 is a one-character change.

**Add a fourth answer type** (audio, a code snippet, a formula): add a value to
[`AnswerType`](app/src/main/java/com/recall/app/data/AnswerType.kt), add a branch in
[`AnswerView`](app/src/main/java/com/recall/app/ui/components/AnswerView.kt), and add a button to
`AnswerTypeSelector` in `AddCardScreen.kt`. The enum's `when` blocks are exhaustive, so the compiler
will point you at every place that needs updating — lean on that.

**Edit an existing card.** There's a `RecallRepository.updateCard` already; it needs a screen. The
quickest route is to reuse `AddCardScreen` with pre-filled initial values.

**Add a daily reminder.** `WorkManager` plus a notification, triggered off
`RecallDao.observeTotalDue`.

**Add tests.** `srs/Sm2.kt` has no Android dependencies, so it's testable with plain JUnit in
`app/src/test/java`. Add `testImplementation("junit:junit:4.13.2")` to `app/build.gradle.kts`.

### Changing the database

**Nothing here will wipe your cards.** The database is deliberately built *without*
`fallbackToDestructiveMigration()`. If you change an `@Entity`, bump `version`, and forget the
migration, the app crashes on launch with this:

```
java.lang.IllegalStateException: A migration from 1 to 2 was required but not found.
Please provide the necessary Migration path via RoomDatabase.Builder.addMigration(Migration ...)
```

That is the good outcome, and it is worth being clear about why. What
`fallbackToDestructiveMigration()` does instead — and what this project used to do — is silently
delete the database and rebuild it empty. No error, no prompt. You would find out when every card you
had ever written was gone. A crash you fix in five minutes is strictly better than data you cannot
get back.

The recipe, in [Migrations.kt](app/src/main/java/com/recall/app/data/Migrations.kt):

1. Change the `@Entity` — add a column, a table, an index.
2. Bump `version` in the `@Database` annotation, `1` → `2`.
3. Write a `Migration(1, 2)` and add it to `MIGRATIONS`.
4. Build once. Room writes `app/schemas/2.json` — **commit it**. Diffing it against `1.json` shows
   exactly what SQL your migration has to produce.
5. Install the new build *over* the old one (don't uninstall) and confirm your data is still there.

`app/schemas/` is the schema history, one JSON file per version, checked into git on purpose: it is
what makes step 4 possible and what Room's migration tests read.

SQLite can `ALTER TABLE ADD COLUMN`, but it cannot drop or retype a column. For those you create a
new table, copy the rows across, drop the old one and rename — there is a worked example in the
comments of that file.

Adding a *value* to an enum like `AnswerType` needs none of this. Enums are stored as text, so the
schema does not change — which is exactly why Code and Audio were added without any migration.

---

## Versions

Kotlin 2.0.20 · AGP 8.6.1 · Gradle 8.9 · Compose BOM 2024.09.03 · Room 2.6.1 · Coil 2.7.0 ·
WorkManager 2.9.1 · `minSdk 26` (Android 8.0) · `targetSdk 35`

## Verified

Built and run on an Android 15 (API 35) emulator, not just compiled. Cold build with the Gradle cache
deleted: **37/37 tasks executed, zero warnings, zero errors**, 17 MB debug APK.

Exercised on-device:

- A card of each of the five answer types, added and then reviewed. Picked files land in the app's
  private storage byte-for-byte, and the audio extension is derived from the MIME type.
- Audio playback: duration read correctly, play/pause, progress tracking, and the player released
  when the card leaves the screen.
- Code answers keep their indentation in both the editor and the review screen.
- The daily reminder end to end: permission prompt on Android 13+, the job scheduled at the right
  delay (`TIME=+18h45m` for 20:00 from 01:14), rescheduling when the time changes rather than
  stacking duplicates, the worker posting *"2 cards are due in Recall"* — a count checked against the
  database and correct — and re-arming itself for the next day afterwards.
- A full review session: reveal, all four grades, "Again" re-queueing, completion screen.
- The scheduler checked against the database directly: after Again then Good a card holds
  `repetition 1, interval 1 day, ease 2.30, lapses 1`, which is what SM-2 specifies.
- Light and dark themes, portrait and landscape, and rotation mid-typing.
- Installing this build over the previous one preserved the existing database, including cards that
  already carried review history.

**The migration safety was tested by deliberately breaking it.** Bumping `version` to 2 with no
migration crashed on launch with the `IllegalStateException` above, and the database was untouched —
1 deck and 5 cards before and after. Adding a real `Migration(1, 2)` then upgraded cleanly:
`user_version` went to 2, the new column appeared with its default, and all five cards kept their
scheduling state. Both halves of the promise hold. That experiment was then reverted — the shipped
schema is still version 1, since none of the new features needed a schema change.

Three bugs were found by running the app that compiling never would have caught, all fixed:

1. **Due counts were always zero.** `strftime('%s','now')` has whole-second precision, so a card
   saved milliseconds ago read as not-yet-due — and because a Room `Flow` only re-emits when the
   *table* changes, that wrong zero stuck forever. Fixed with a millisecond clock expression plus a
   ticker that re-subscribes once a minute.
2. **"Again" then "Good" erased the lapse.** The re-queued card was the stale pre-rating snapshot, so
   grading it again recomputed from the original state and overwrote the ease penalty — failing a
   card had no lasting effect, which defeats the algorithm. `review()` now returns the rescheduled
   card, and that is what goes back in the queue.
3. **Rotating the phone wiped the add form.** `remember` does not survive Activity recreation;
   switched to `rememberSaveable`. The deck picker also no longer keys off the live `decks` list,
   which would have reset your choice the moment you created a deck from that screen.

Not tested: a physical phone. Hardware-specific behaviour — a manufacturer's photo picker, an unusual
aspect ratio, a vendor's aggressive background-process killer interfering with the reminder — remains
unverified.
