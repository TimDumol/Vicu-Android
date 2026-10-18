# Renaming the repository to Vicu-Mobile

The repository holds the Android and the iOS app, so `rendyhd/Vicu-Android` became
`rendyhd/Vicu-Mobile` on 2026-10-18, after the 2.1.0 release. One repository for both platforms;
the desktop app stays in `rendyhd/vicu`. Nothing in the build depends on the repository name.
GitHub redirects the old URL (web, clone and the API, which Obtainium reads), so existing links
and installs keep working. Never create a new repository named `Vicu-Android`: it would end the
redirect.

What was done:

1. On GitHub: the repository was renamed (Settings > General > Repository name).
2. Locally, in each checkout:
   ```bash
   git remote set-url origin https://github.com/rendyhd/Vicu-Mobile.git
   ```
   Renaming the folder to `vicu-mobile` is optional. The desktop checkout is still expected next
   to it as `../vicu` (`SharedContractIdentityTest`, the UI harnesses), which does not change.
3. Links: the README (Obtainium and Releases), the unreleased 2.2.0 notes and the docs name the
   new repository. Older release notes (`docs/releases/`) keep their links; GitHub redirects them.
   Still to do: the desktop repository's README, which links to Vicu-Android once.
4. Releases: one tag and one version (`version.properties`) per release for both platforms.
   Publishing the GitHub release runs `release.yml` (the signed APK); the iOS build goes to
   TestFlight from a Mac with `iosApp/scripts/testflight.sh` (`docs/ios.md`, Release).
