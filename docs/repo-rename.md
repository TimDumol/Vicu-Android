# Renaming the repository to Vicu-Mobile

The repository holds the Android and the iOS app, so `rendyhd/Vicu-Android` becomes
`rendyhd/Vicu-Mobile`. One repository for both platforms; the desktop app stays in
`rendyhd/vicu`. Nothing in the build depends on the repository name. GitHub redirects the old
URL (web, clone, Obtainium), so existing links and installs keep working.

1. On GitHub: Settings > General > Repository name: `Vicu-Mobile`.
2. Locally:
   ```bash
   git remote set-url origin https://github.com/rendyhd/Vicu-Mobile.git
   ```
   and rename the folder to `vicu-mobile` if you like. The desktop checkout is still expected
   next to it as `../vicu` (`SharedContractIdentityTest`, the UI harnesses), which does not change.
3. Update the links that name the old repository:
   - `README.md`: the Obtainium link and the Releases link.
   - The desktop repository's README, which links to Vicu-Android once.
   - Older release notes (`docs/releases/`) can keep their links; GitHub redirects them.
4. Releases: one tag and one version (`version.properties`) per release for both platforms.
   Publishing the GitHub release runs `release.yml` (signed APK) and `ios.yml` (TestFlight).
