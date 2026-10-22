# Vicu for Android and iOS

A task manager for Android, iPhone and iPad, powered by [Vikunja](https://vikunja.io/).

**Get it:** [iPhone and iPad (TestFlight)](https://testflight.apple.com/join/c6wuvXrH) · [Android (Google Play beta)](#download) · [Android APK](https://github.com/rendyhd/Vicu-Mobile/releases/latest) · Desktop: [Vicu](https://github.com/rendyhd/Vicu)

![slide1 001](https://github.com/user-attachments/assets/83925b87-cfbb-4fc8-b796-f69b9dc303aa)

![License](https://img.shields.io/badge/license-MIT-blue)
![Platform](https://img.shields.io/badge/platform-Android%208%2B%20%7C%20iOS%2017%2B-brightgreen)
![Built with](https://img.shields.io/badge/kotlin%20+%20compose%20multiplatform%20+%20material%203-7F52FF)

<!-- screenshot: main-screens -->

## Contents

- [What is Vicu for Android and iOS?](#what-is-vicu-for-android-and-ios)
- [Capture from anywhere](#capture-from-anywhere)
- [Add tasks by voice](#add-tasks-by-voice)
- [Natural language input](#natural-language-input)
- [Organize your work](#organize-your-work)
- [Working with tasks](#working-with-tasks)
- [Reminders](#reminders)
- [Share projects with others](#share-projects-with-others)
- [Widgets](#widgets)
- [Sync, offline and your other devices](#sync-offline-and-your-other-devices)
- [Look and feel](#look-and-feel)
- [iPhone and iPad](#iphone-and-ipad)
- [Getting started](#getting-started)
- [Building from source](#building-from-source)

## What is Vicu for Android and iOS?

Vicu for Android and iOS is the mobile companion to [Vicu](https://github.com/rendyhd/Vicu), bringing the same focused task management to your phone and tablet. It connects to any Vikunja server and organizes your tasks around when they need to happen: tasks land in your **Inbox**, get scheduled into **Today** or **Upcoming**, sit in **Anytime** as your open backlog, and end up in the **Logbook** when done. Widgets, reminders, Siri and location reminders keep you on top of things without opening the app.

Android, iPhone and iPad run the same app from one Kotlin codebase (Compose Multiplatform and Material 3), so they behave the same way. The desktop app and the mobile app talk to the same Vikunja server and read your data by the same rules ([`docs/cross-app-semantics-v1.md`](docs/cross-app-semantics-v1.md)), so a due date, a custom list, a task order or a project review means the same thing on your phone and on your computer. They also share one design system, so they look alike.

## Capture from anywhere

- **Quick add**: tap **+** for a compact sheet right above the keyboard. Type a task in your own words, add a notes line, and watch the chips (project, labels, date, priority) follow what you type. Expand it, or drag the handle up, to continue in the full editor. **Keep add-task open** lets you add several in a row.
- **Drag + onto a day or project** in Today, Upcoming, Anytime or a project to open quick add with that date or project already set.
- **Hold + to say a task** (see [Add tasks by voice](#add-tasks-by-voice)).
- **Share to Vicu**: share an article, link, photo or file from any app and it becomes a task with the title and notes filled in. Shared files and images are attached.
- **On Android**: a **Quick Settings tile** in the notification shade, a **Quick add widget** with mic and + buttons, and the app-icon shortcuts **Add task** and **Add by voice**.
- **On iPhone and iPad**: an **Add task** control for Control Center, the Lock Screen and the Action Button (iOS 18), "Add a task in Vicu" with **Siri**, and **Add task** when you hold the app icon.
- Works offline: tasks added without a connection are sent once you are back online.

## Add tasks by voice

- **Hold + to say a task.** Your words show in a card as you speak, with the date, project and labels picked out the way quick add reads them. Let go to add the task, slide down to cancel. A tap still opens quick add.
- **The task goes to the list on screen**: a project's tasks go to that project, Today's get today's date, unless you say otherwise.
- **Siri on iPhone and iPad**: "Hey Siri, add a task in Vicu" asks what the task is and adds it without opening the app.
- **Android**: **Add by voice** on the app icon, the mic on the Quick add widget and the mic in the Tasks widget.
- With TalkBack, the + button has a **Say a task** action.
- Your default reminder is added when the task has a due date.

## Natural language input

Type tasks the way you think. Vicu parses freeform text into structured fields as you type, with colour highlighting and autocomplete suggestions.

| Token | Example | Effect |
|-------|---------|--------|
| Days | `today`, `tomorrow`, `friday`, `this friday`, `next Monday`, `next week`, `next month`, `in 3 days`, `in 2 weeks` | Sets the due date (date only) |
| Calendar dates | `jan 15`, `15 jan`, `march 3rd`, `2026-10-15`, `10/15` | Sets the due date; a date that has already passed this year means next year |
| Times | `tomorrow 3pm`, `today at 14:00`, `fri 9:30am`, `in 2 hours`, `in 30 minutes`, `at 5pm` | Sets due date and time |
| `!` | `Buy milk !`, `! Buy milk`, `Buy milk!` | Due today |
| Priority | `p1`–`p4`, `!urgent`, `!critical`, `!high`, `!medium`, `!med`, `!low` | Sets priority |
| Labels | `@shopping`, `@"grocery list"` | Applies labels |
| Projects | `#work`, `#"side project"` | Assigns to project |
| Recurrence | `every 3 days`, `every 2 weeks`, `every friday`, `daily`, `weekly`, `biweekly` | Sets repeat interval |
| Places | `when I get home`, `when I arrive at work`, `when I leave work` | Location reminder at a saved place |

Everything that isn't a recognized token becomes the task title. Tokens can appear anywhere in the input and are shown as chips; tap a chip to remove it.

How dates and recurrence are read:

- A bare weekday or `this <weekday>` is the next one on or after today, so `tuesday` typed on a Tuesday is today. `next <weekday>` is that day in the following week (weeks start on Monday), and `next week` is next Monday.
- A date without a time is stored as a date only. A time without a date means today if it is still ahead, otherwise tomorrow. A time can come before or after the date (`3pm tomorrow`).
- Three-letter weekdays (`mon`, `tue`, `wed`, `thu`, `fri`, `sat`, `sun`) only count after `on`, `next`, `this`, `by` or `due`, or before a time, so "Buy sun cream" has no date. Full weekday names always count.
- Connector words go with the date: "Submit report by friday" becomes "Submit report", "Call at 9am" becomes "Call".
- Slash dates follow your phone's order (`10/15` is October 15 in en-US, `5/11` is 5 November in en-GB). When the first number can't be a month, the order flips (`15/10`).
- Words about the past ("last friday", "yesterday", "2 days ago"), a date inside another word ("Monday's") and "weekend" are not dates. A range uses its first date ("Trip friday to sunday" is due Friday).
- `daily`, `weekly`, `monthly`, `yearly`, `annually`, `biweekly` and `fortnightly` make a task recurring only as the last word ("Water plants daily"); "weekly standup" stays a title. `every friday` repeats weekly and sets the due date to the next Friday (today counts), unless the text has another date.
- The `!` shortcut works even when natural language parsing is turned off. An explicit date wins over it ("Call mom tomorrow!" is due tomorrow).

These rules are shared with the desktop app and checked against the same test corpus ([`test-fixtures/nlp-corpus-v1.json`](test-fixtures/nlp-corpus-v1.json)). Two syntax modes are available in **Settings, General, Input Parsing**: **Todoist** (default; `@` for labels, `#` for projects) and **Vikunja** (`*` for labels, `+` for projects).

## Organize your work

### Smart lists

| List | What it shows |
|------|---------------|
| **Inbox** | Your Inbox project: where new tasks land. Drag tasks into your own order, or sort it with its sort button |
| **Today** | Due today, an Overdue section, and your routines for the day |
| **Upcoming** | Due from tomorrow on, grouped by day with sticky day headers, and by project within each day |
| **Anytime** | Every open task outside the Inbox |
| **Logbook** | Completed tasks, grouped by the day you completed them; optionally only the last few days |

Today, Upcoming and Overdue follow your local calendar day and roll over at midnight. **Move dated tasks out of Inbox** keeps the Inbox for undated tasks only. The **bottom bar** keeps the Inbox first and lets you choose the other three slots: any smart list, project or custom list, each with its own icon and colour.

### Projects

- The drawer lists your projects with their subprojects, a **progress ring** of how much is done, and optionally the number of open tasks (**Show project progress** and **Show task count** under **Settings, Projects, Display**).
- **Sections** split a project into collapsible groups with headers and counts.
- A project's **⋮** menu has **Edit project** (name, parent, colour, sharing), **Add subproject**, **Sort tasks**, **Set as Inbox**, **Mark reviewed** (with Undo), **Archive** and **Delete**. The **+** on the Projects header in the drawer adds a project.
- **Settings, Projects** lists every project in drawer order: tap one to edit it, or hold and drag to reorder it within its level (TalkBack offers Move up and Move down). Archived projects sit in a collapsed **Archived** group with **Restore**.

### Task order

Each project can be sorted its own way: **Due date, then manual** (the default), **Due date, then priority**, **Manual**, **Priority**, **Title**, **Newest first** or **Oldest first**. Pick an order for every project in **Settings, Projects, Display**, or for one project with **Sort tasks** in its menu; a project without its own order follows its parent's. Project and Inbox widgets follow the same order, and it syncs with your other devices and the desktop app.

In Manual order you can drag every task; in the default order the undated ones; in the sorted orders none. Today, Upcoming, Anytime and custom lists keep their own order.

### Custom lists

Build your own filtered views. A custom list can filter on projects, labels, priority, a **due date window** (overdue, today, this week, this month, has a due date, no due date; today, this week and this month have an **Include overdue** option) and **Changed** (today, or in the last 3, 7 or 30 days). Under **Shared projects** it can also filter on **Assigned** (to me, to someone else, to nobody), **Comments** (unread, has comments) and **Only tasks in shared projects**. Lists sort by due date, created, updated, priority, title, completed or position.

Custom lists are stored on your Vikunja server, so the desktop app shows the same lists with the same results. Put one in the bottom bar or on a widget.

### Labels

Labels with custom colours, a multi-select picker that creates new labels inline, and a page per label. Drag labels in the drawer to order them.

### Routines

Routines are recurring habits that live next to your tasks: a name, up to five times a day (each with a reminder and a follow-up) and a repeat: daily, weekdays, every 1 to 4 weeks, or a number of days after you complete it. Today lists the routines still open for the day; complete or skip them there, from the notification or from the Routines widget. A missed day is "Not logged"; routines are never overdue.

Each routine keeps its history, with CSV export. History older than 400 days is archived on your Vikunja server and stays available. **Pause all reminders** is in the Routines menu. Routines are optional: **Settings, General, Routines** has **Enable routines** and **Show routines in Today**.

### Project review

Step through your projects on a cadence (14 days by default, or per project) to keep them current. Review rows have a **Mark reviewed** button, and **Settings, Projects** lists the projects you excluded from review. The review status is stored on the server and matches the desktop app.

### Search

Search titles, descriptions and completed tasks; matches already on the device show at once.

## Working with tasks

- **The task editor** is a sheet over your list with an editable headline, plain notes, and its properties as rows in one card; the ones you have not set wait under **Add**. When, Priority, Remind and Repeat offer their common choices right there, and **Today**, **Tomorrow**, **This weekend** and **Next week** move a task with one tap, with Undo. It saves the moment you dismiss it.
- **The When sheet**: one sheet for every date and time. Type a date in your own words ("tomorrow 9am", "next friday"), pick a quick choice (the coming weekdays are listed by name), use the calendar (weeks start on Monday) or set a time in your phone's clock style.
- **Dates read the same way everywhere**: "Today", "Tomorrow", weekdays, then dates. A date without a time stays a date; the year shows only when it is not this one.
- **Swipe gestures**: swipe right to complete, swipe left to schedule (choose what scheduling does in **Settings, Gestures**). The swipe shows a word beside the icon, deepens its tint the further you go, and gives a tap where the action commits. The **Gesture Guide** in Settings shows them all.
- **Completing a task**: the checkbox animates and you feel a small tap. The row stays for 5 seconds (longer while you touch it), then collapses. One **Completed** message with **Undo** covers a run of completions ("3 completed"). An optional completion sound.
- **Subtasks** nested under their parent; show them beneath the parent in lists or keep lists minimal.
- **Recurring tasks**: daily, weekly, monthly or any interval. Undo of a completed repeating task puts its dates back.
- **Priority** shows as one to three bars (low, medium, high) and a filled square for urgent, read out by TalkBack.
- **Attachments** of any type, up to the server's size limit: add from files, photos or by pasting an image into the notes (Android); open, share, or **Save** to Downloads (Android) or Files (iPhone and iPad).
- **Reorder by dragging**, with a tick as the task passes each position.

## Reminders

- **Per-task reminders** at a set time or relative to the due date, with **Mark complete** and **Snooze** on the notification.
- **Default reminder** (**Settings, Notifications**): every new task with a due date gets it. A reminder you pick yourself wins, and changing the date of an existing task never adds one.
- **Location reminders**: get reminded when you **arrive at** or **leave** a saved place (Home, Work, ...), a searched address or where you are now, within 100, 200 or 500 m. Choose **At a location...** among a task's reminders, or type or say "Call mom when I get home". It fires once, also with the app closed, and never for a task that is done or deleted. Location reminders need location access set to **Always**; Vicu explains why before it asks.
- **Saved places** live in **Settings, Notifications, Places**. A place's name and the reminders at it sync with your other devices and the desktop app; where the place is stays on the phone that saved it. A place saved on another phone shows "Not set on this phone", with **Set location**.
- **Daily summary**: a morning and an afternoon notification with your overdue, due today and tomorrow's tasks, at times you choose.

## Share projects with others

Share a project with the people you live or work with. Everything about sharing appears only on projects someone else can see; if you share nothing, Vicu looks and works exactly as before.

- **Share**: open a project, tap **⋮**, **Edit project**, and scroll to **Shared with**. Type a username, pick **Can view**, **Can edit** or **Admin**, and tap **Share**. Change access from the menu next to someone's name, or remove them with **×** (with **Undo**). Only admins can share, and sharing a project shares its subprojects.
- **Assign**: rows show who is assigned, you first. A task's **Assign** chip lets you tick people; Vikunja notifies them. Someone who can only view the project cannot be picked.
- **Comments**: rows show the comment count, with a dot for unread ones. Open the thread from the count or the comments chip. Type **@** to mention someone, hold one of your own comments to edit or delete it, and use the bell (**Notify me**) to follow the task. Opening a thread marks it read on every device.
- **Custom lists for shared work**: Assigned to me, Waiting on others, Unread comments, Shared this week (see [Custom lists](#custom-lists)); a widget can show them too.
- **Offline**: assignments and comments made offline wait in the queue; a comment still waiting says **Waiting to sync**.

The people you share with need an account on the same Vikunja server. If you sign in with an API token, give it the permissions for task comments, task assignees and project sharing in Vikunja; Vicu tells you which one is missing instead of signing you out. **Notify me** needs a password or single sign-on sign-in, because Vikunja does not let API tokens follow tasks.

## Widgets

- **Tasks widget**: Today, Inbox, Upcoming, Anytime, any project or custom list, with tap-to-complete checkboxes, dates and priority marks.
- **Routines widget**: today's routines with their times, completed with a tap.
- **Quick add widget** (Android): mic and + buttons.
- On Android the widgets are built with Glance, follow the Vicu colours (or your wallpaper colours), refresh right after a tap or an edit (Android 12 and later), and the Tasks widget's transparency is adjustable. **Settings, General, Widget** sets whether + uses smart add and whether a tap opens the list in context.
- On iPhone and iPad the widgets are WidgetKit widgets; choose the list in **Edit Widget**. A completion from a widget is sent the next time Vicu runs.

## Sync, offline and your other devices

- **Offline-first.** Your tasks live in a local database, so the app opens and works without a connection. Changes you make offline are queued, sent in order when the connection is back, and kept across sign-out and cache clears until you choose to discard them. Failed changes are shown with retry and discard. A background sync runs every 30 minutes on Android, and whenever iOS allows it on iPhone and iPad.
- **Your edits never overwrite another device**: Vicu sends only the fields you changed, and changes go one at a time, so bulk actions also work on Vikunja servers that use SQLite.
- **Settings that follow you**: the **Inbox** project, the **task order** of every project, your **custom lists**, saved **places** (their names) and location reminders are the same on every phone, iPhone, iPad and the desktop app. A new device suggests your Inbox during setup. Device choices such as the theme, notifications and the bottom bar stay on each device ([`docs/settings.md`](docs/settings.md) lists every setting and where it lives).
- **Pull to refresh** any list; a task deleted on another device disappears without a full refresh.
- **Clear Cache & Re-sync** (**Settings, General**) fetches everything again; **Discard unsynced changes** also drops custom-list and settings changes that never reached the server.
- **Signing in to another account** starts clean, without parts of the previous sign-in.

**Hidden tasks.** Vikunja has no place for an app's own data, so Vicu keeps custom lists, routines (and their archive) and synced settings in tasks it marks done and hides from every list, count, reminder, widget and search. You may see them in other Vikunja apps; leave them in place. Each person's Vicu uses only the hidden tasks they created, so a shared Inbox never mixes people's data. More in [`docs/hidden-tasks.md`](docs/hidden-tasks.md).

## Look and feel

- **One design system with the desktop app**: the same colours, type, spacing, date wording and motion, from one shared token file. Every colour pair is checked for readable contrast in light and dark.
- **Light, dark and system themes**, in the Vicu colours by default. On Android 12 and later, **Use device colours** takes your wallpaper colours instead.
- **Motion**: tabs cross-fade, a project slides in from the side, predictive back shows where you are going before you let go, lists move smoothly when rows are added, completed or reordered, and counts roll. When you empty Today it says **All clear** and offers the next task. With animations turned off in your system settings, all of this becomes a plain fade or nothing at all.
- Large titles fold into the top bar as you scroll, and the + button shrinks to an icon. A **left-handed** option moves the + button to the other side.
- **Accessibility**: TalkBack actions on every task row (including Move up and Move down wherever a list can be reordered, and Say a task on +), Dynamic Type on iPhone and iPad, and haptic feedback for completions, swipes and reordering.
- **Privacy**: Vicu collects nothing; your data goes only to your own Vikunja server. Release builds log no requests, backups leave out the task database and your sign-in, and on iPhone and iPad your sign-in stays in the Keychain on that device. See the [privacy policy](docs/privacy-policy.md).

## iPhone and iPad

The iOS app is the same app as on Android, with iOS gestures and system settings: swipe back from the edge, Dynamic Type, and light and dark appearance. A few things work differently because of the platform:

- iOS keeps a limited number of notifications waiting, so Vicu plans the soonest reminders ahead and tops them up whenever something changes.
- Background sync runs when iOS allows it; **Settings, General, Background activity** shows the last runs.
- Control Center, the Lock Screen, the Action Button, Siri and the home-screen quick action take the place of Android's Quick Settings tile.
- Sharing to Vicu opens a small native sheet that adds the task to your Inbox.
- Some single sign-on providers, Google among them, do not allow sign-in inside an app; use your password or an API token there.
- Device colours (Material You) and pasting an image into the notes are Android only.

The full list is in [docs/ios.md](docs/ios.md#differences-from-android).

## Getting started

### Requirements

- A [Vikunja](https://vikunja.io/) server running **version 2.4.0 or newer**: your own or [Vikunja Cloud](https://vikunja.cloud/).
- Android 8.0 or newer, or iOS / iPadOS 17 or newer.

### Download

**iPhone and iPad** (iOS 17 or newer): [join the public beta on TestFlight](https://testflight.apple.com/join/c6wuvXrH). Install Apple's TestFlight app, open the link on your device, and new versions arrive automatically.

**Android** (8.0 or newer), any of:

- **Google Play beta**: join the [vicu-beta Google Group](https://groups.google.com/g/vicu-beta) with the Google account on your phone, then [become a tester](https://play.google.com/apps/testing/com.rendyhd.vicu) and install Vicu from [Google Play](https://play.google.com/store/apps/details?id=com.rendyhd.vicu). Updates come through Play.
- [Add to Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/rendyhd/Vicu-Mobile) to follow the GitHub releases.
- Or grab the latest APK from [GitHub Releases](https://github.com/rendyhd/Vicu-Mobile/releases).

The Play version and the GitHub APK are the same build signed with the same key, so you can switch between them without reinstalling or losing local data. On Android, a server with a certificate from your own certificate authority works once that certificate is installed on the phone.

### First launch

1. Enter your Vikunja server URL.
2. Sign in with **OIDC** (single sign-on), your **username and password** (with TOTP two-factor), or an **API token**.
3. Choose your **Inbox** project. If another device already chose one, Vicu suggests it.
4. Allow notifications when Vicu asks, so reminders and daily summaries can reach you.
5. Optionally add a widget, the Quick Settings tile (Android) or the Add task control (iPhone and iPad), and save your places for location reminders.

## Building from source

Kotlin with Compose Multiplatform and Material 3, with Ktor, Room and Koin underneath. The `shared` module holds the screens, view models, sync engine and data layer; `app` is the Android app with its Glance widgets, and `iosApp` is the iOS app with its WidgetKit widgets and share extension. Use JDK 17 or newer; the iOS app needs a Mac with Xcode 26 and XcodeGen.

```bash
./gradlew test
```

```bash
./gradlew installDebug
```

| Command | What it does |
|---------|--------------|
| `./gradlew test` | All JVM unit tests (`:app` and `:shared`) |
| `./gradlew lint` | Android lint |
| `./gradlew assembleDebug` | Debug APK (`com.rendyhd.vicu.debug`, installs beside the release) |
| `./gradlew installDebug` | Build and install on a connected device or emulator |
| `./gradlew :shared:iosSimulatorArm64Test` | Shared tests on the iOS simulator |
| `iosApp/scripts/generate.sh` | Generate the Xcode project from `iosApp/project.yml` |

Building and testing the iOS app is described in [docs/ios.md](docs/ios.md); the architecture and conventions are in [`CLAUDE.md`](CLAUDE.md). The rules shared with the desktop app are in [`docs/cross-app-semantics-v1.md`](docs/cross-app-semantics-v1.md) and [`docs/design-system-v1.md`](docs/design-system-v1.md), with test fixtures in [`test-fixtures/`](test-fixtures/) that are identical in both repositories. Release notes for every version are in [`docs/releases/`](docs/releases/).

Found a bug or have an idea? [Open an issue](https://github.com/rendyhd/Vicu-Mobile/issues).

## See also

- **[Vicu](https://github.com/rendyhd/Vicu)**: the desktop app for Windows, macOS and Linux, with Quick Entry, Quick View, voice input, Obsidian and browser links
- **[Vikunja](https://vikunja.io/)**: the open-source backend that powers Vicu
- **[Privacy policy](docs/privacy-policy.md)**: Vicu collects nothing; your data goes only to your own Vikunja server
- **[docs/ios.md](docs/ios.md)**: building and testing the iOS app, and where it behaves differently from Android

## License

[MIT](LICENSE)
