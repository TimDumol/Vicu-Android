# Vicu privacy policy

Last updated: 20 October 2026

Vicu is a task manager for Android, iPhone and iPad that works with a [Vikunja](https://vikunja.io/) server you choose. It is made by Rendy Jansen as an open-source project ([source code](https://github.com/rendyhd/Vicu-Mobile)).

## The short version

Vicu does not collect your data. The developer runs no server for Vicu and receives nothing from the app: no account, no analytics, no crash reports, no advertising and no tracking.

## What the app stores and sends

- **Your tasks, projects, labels, comments, attachments and settings** are sent to and stored on the Vikunja server whose address you enter when you sign in. That server is run by you, your organisation or a hosting provider you chose, and its own privacy policy applies to the data on it.
- **A copy of that data** is kept on your device so the app works offline. Changes you make offline wait on the device until they reach your server.
- **Your sign-in** (a session token, or an API token you paste in) is stored on the device, encrypted (the Android Keystore; the iOS Keychain). Your password is sent only to your Vikunja server when you sign in, and is not stored.
- **Images you open** in the app, such as people's avatars, are loaded from your Vikunja server.
- **Links in task descriptions** open in your browser only when you tap them.
- **Location reminders and saved places** (Home, Work, ...) stay on your device. They are not sent to your Vikunja server or anywhere else, and they are excluded from backups.
- **Place search**: when you search for a place for a location reminder, the text you type is sent to a search service, together with your approximate position (rounded to about a kilometre on Android) so nearby places come first. On Android that is [Photon](https://photon.komoot.io/) by komoot, which uses OpenStreetMap data; on iPhone and iPad it is Apple Maps. Nothing else is sent with it.

Apart from place search, Vicu sends nothing to any other party. Its other connections are to your Vikunja server, and to your identity provider when you sign in with single sign-on.

## Permissions

- **Notifications and exact alarms**: to remind you of tasks and routines at the time you set. Reminders are planned on the device.
- **Location** (optional, "all the time" / "Always"): only for location reminders, to notify you when you arrive at or leave a place you chose, also when the app is closed. The position is used on the device to recognise those places and is not stored or sent anywhere; the place search above is the only time a position leaves the device. Without this permission everything else works.
- **Network**: to reach your Vikunja server.
- **Files and photos** you pick yourself: to attach them to a task. The app only sees the files you choose.

## Backups

The local database and your sign-in are excluded from the phone's cloud backups and device transfers. On iPhone and iPad, widget contents and shares waiting to be added are also excluded from iCloud backups.

## Deleting your data

Signing out (Settings, General) removes the local copy from the device; changes that have not reached your server yet are kept until you discard them, and the app asks before discarding them. Uninstalling the app removes everything it stored on the device. Data on your Vikunja server is deleted through that server, for example by deleting tasks or your account there.

## Children

Vicu is not directed at children.

## Changes

Changes to this policy are published on this page, with the date above.

## Contact

Questions about this policy: open an issue at [github.com/rendyhd/Vicu-Mobile/issues](https://github.com/rendyhd/Vicu-Mobile/issues).
