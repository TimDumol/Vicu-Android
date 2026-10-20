# Adding tasks by voice

What Vicu offers for adding a task by voice on each platform, how it works, and the options left
for later. The text is read like quick add in every case (`TaskParser`, the same rules as the
desktop): "Call mom tomorrow 3pm #Family @phone" is a task "Call mom" due tomorrow at 15:00 in the
Family project with the label phone.

## How it works

`TextTaskCreator` (`shared/.../util/`) turns one sentence into a task without a screen: the parser
with the user's quick-add settings, the same field rules as the quick add sheet with nothing picked
by hand (`resolveEntryFields`), labels matched by name or created, the default reminder for a due
date, the project the text names or else the Inbox. The task is stored on the device and queued
(`TaskRepository.createQueued`), so the answer comes at once and nothing is lost offline; the sync
sends it. It answers with a confirmation such as `Added “Call mom”, tomorrow, 15:00`.

A place phrase for a saved place sets the task's location reminder (`PlacePhrases`, English): "when
I get home", "when I'm at work", "when I arrive at Mom's" (arrive) or "when I leave work", "when I'm
leaving home" (leave), at the end of the sentence or at its start ("When I get home, call mom").
"the" or "my" before the place is fine. The phrase is taken out of the title, and the reply says it:
`Added “Call mom”, when you arrive at Home`, plus a note when location access is not set to Always.
A place that is not saved ("when I get to the supermarket") stays in the title and sets nothing.

Siri itself sends "remind me ..." without an app name to Apple Reminders; iOS has no default app
setting for reminders, so the user names Vicu ("Remind me in Vicu", then "call mom when I get
home").

## iOS

- **Siri**: "Add a task in Vicu" (also "New Vicu task", "Add to Vicu", "Remind me in Vicu").
  Siri asks "What's the task?", the user says it, and Siri reads back what was added. Two turns:
  App Shortcut phrases cannot carry free text, so the task cannot be part of the first sentence.
  `AddTaskIntent` (`iosApp/Vicu/`) runs in the app's process without opening it
  (`openAppWhenRun = false`) and is allowed on a locked phone (`.alwaysAllowed`: it adds and
  reads nothing back), so it works hands-free and in CarPlay.
- **Shortcuts and the Action Button**: "Dictate Text" followed by Vicu's "Add a task" with the
  dictated text as its Task adds in one step.
- **In the app**: the keyboard's microphone in quick add.

## Android

- **Quick add widget** (2x1): the mic opens the system speech screen ("What's the task?") over the
  home screen, adds the task without opening the app and shows the confirmation as a toast; + opens
  quick add. The Tasks widget has the same mic next to its +.
- **App icon shortcuts** (long-press): "Add task" (quick add) and "Add by voice".
- **In the app**: the keyboard's microphone in quick add.
- `VoiceAddActivity` uses `RecognizerIntent`, the system's speech screen, so Vicu needs no
  microphone permission. Without a speech service it opens quick add instead.

## For later

- **One sentence on iOS (Siri AI)**: iOS 27 has an App Schema domain for reminders
  (`.reminders`, `createReminder` with a title, due date, list and tags). Adopting it lets
  "Add call mom tomorrow to Vicu" work in one sentence where Siri AI is available: iPhone 15 Pro
  or newer, English first, not in the EU on iOS at launch (September 2026). It needs Xcode 27 and
  an `@available(iOS 27, *)` intent beside `AddTaskIntent`, which stays for everyone else.
- **Gemini on Android**: Google Assistant's App Actions are gone from phones and Gemini does not
  run them. Gemini reaches third-party apps through AppFunctions (`androidx.appfunctions`,
  Android 16 and later), still a private preview for trusted testers in mid 2026. An `addTask`
  AppFunction would call `TextTaskCreator`; it is worth building once Google opens the program.
  Android Auto has no category for task apps, so there is no in-car voice path on Android today.
- **Parsing spoken text**: dictation writes "3 p.m.", "3 PM" or "hashtag errands" where typing
  gives "3pm" and "#errands". The parser and the NLP corpus are shared with the desktop
  (`test-fixtures/nlp-corpus-v1.json`), so spoken forms go in the corpus first, in both repos.
