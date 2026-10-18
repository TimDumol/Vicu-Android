#!/bin/bash
# Archives the release app on this Mac and uploads it to TestFlight, without GitHub Actions.
#
# Signing is automatic and uses the Apple account signed in to Xcode (Settings > Accounts) with
# DEVELOPMENT_TEAM from Configuration/Local.xcconfig, or an App Store Connect API key when
# ASC_KEY_PATH, ASC_KEY_ID and ASC_ISSUER_ID are set. The version is version.properties;
# BUILD_ATTEMPT (default 0) uploads the same version again. --no-upload stops after the archive.
#
# The optimized Kotlin link takes about 10 minutes and needs a Mac with 16 GB of memory.
set -euo pipefail
cd "$(dirname "$0")/.."

upload=1
for arg in "$@"; do
  case $arg in
    --no-upload) upload=0 ;;
    *) echo "usage: $0 [--no-upload]" >&2; exit 2 ;;
  esac
done

team=${APPLE_TEAM_ID:-$(sed -n 's/^[[:space:]]*DEVELOPMENT_TEAM[[:space:]]*=[[:space:]]*//p' \
  Configuration/Local.xcconfig 2>/dev/null | tr -d '[:space:]')}
if [ -z "$team" ]; then
  echo "No signing team: put DEVELOPMENT_TEAM = <team id> in iosApp/Configuration/Local.xcconfig" >&2
  exit 1
fi

auth=()
if [ -n "${ASC_KEY_PATH:-}" ]; then
  auth=(-authenticationKeyPath "$ASC_KEY_PATH"
        -authenticationKeyID "${ASC_KEY_ID:?ASC_KEY_ID is needed with ASC_KEY_PATH}"
        -authenticationKeyIssuerID "${ASC_ISSUER_ID:?ASC_ISSUER_ID is needed with ASC_KEY_PATH}")
fi

memory_gb=$(( $(sysctl -n hw.memsize) / 1073741824 ))
if [ "$memory_gb" -lt 16 ]; then
  echo "warning: this Mac has $memory_gb GB of memory; the optimized Kotlin link needs about 10 GB" >&2
fi

scripts/generate.sh
version=$(sed -n 's/^MARKETING_VERSION = //p' Configuration/Version.xcconfig)
build=$(sed -n 's/^CURRENT_PROJECT_VERSION = //p' Configuration/Version.xcconfig)
out=build/testflight/$version-$build
archive=$out/Vicu.xcarchive
echo "Vicu $version ($build), team $team -> $archive"

rm -rf "$out"
xcodebuild archive \
  -project Vicu.xcodeproj -scheme Vicu -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$archive" \
  -allowProvisioningUpdates ${auth[@]+"${auth[@]}"} \
  DEVELOPMENT_TEAM="$team"

if [ "$upload" -eq 0 ]; then
  echo "Archived (not uploaded): $archive"
  exit 0
fi

# ExportOptions.plist has destination upload: the export sends the build to App Store Connect.
xcodebuild -exportArchive \
  -archivePath "$archive" \
  -exportPath "$out/export" \
  -exportOptionsPlist ExportOptions.plist \
  -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}

echo "Uploaded Vicu $version ($build). It shows in TestFlight once App Store Connect has processed it."
