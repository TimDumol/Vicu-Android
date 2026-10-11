#!/bin/sh
# Signs a simulator build with your Apple Development certificate instead of ad hoc.
#
# An ad-hoc signature has no team, and without one iOS cannot resolve the widgets' App Intents
# entities: in the simulator the Tasks widget then ignores the list chosen in Edit Widget and
# shows Today. Builds for a device are always signed with a team and do not need this.
#
#   scripts/sign-for-simulator.sh build/dd/Build/Products/Debug-iphonesimulator/Vicu.app
#   xcrun simctl install <simulator> build/dd/Build/Products/Debug-iphonesimulator/Vicu.app
#
# SIGN_IDENTITY picks the certificate; the first Apple Development one is used otherwise.
set -eu
app=${1:?usage: sign-for-simulator.sh path/to/Vicu.app}
identity=${SIGN_IDENTITY:-$(security find-identity -v -p codesigning | sed -n 's/.*"\(Apple Development[^"]*\)".*/\1/p' | head -n 1)}
[ -n "$identity" ] || { echo "No Apple Development certificate in the keychain" >&2; exit 1; }

# Only the App Group: with application-identifier but no provisioning profile the simulator
# refuses to start the extension.
group=$(/usr/libexec/PlistBuddy -c 'Print :VicuAppGroup' "$app/Info.plist")
entitlements=$(mktemp)
trap 'rm -f "$entitlements"' EXIT
cat > "$entitlements" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>com.apple.security.application-groups</key>
	<array>
		<string>$group</string>
	</array>
</dict>
</plist>
PLIST

sign() { codesign --force --sign "$identity" "$@" >/dev/null 2>&1; }

# Inside out: the debug dylibs, each extension, then the app.
for extension in "$app"/PlugIns/*.appex; do
    [ -e "$extension" ] || continue
    for lib in "$extension"/*.dylib; do [ -e "$lib" ] && sign "$lib"; done
    sign --entitlements "$entitlements" "$extension"
done
for lib in "$app"/*.dylib; do [ -e "$lib" ] && sign "$lib"; done
sign --entitlements "$entitlements" "$app"
codesign -dv "$app" 2>&1 | grep TeamIdentifier
