#!/bin/sh
# Xcode build phase: builds the shared Kotlin framework for the configuration and SDK being built
# (Gradle's embedAndSignAppleFrameworkForXcode reads them from Xcode's environment).
set -eu
cd "$SRCROOT/.."
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true)
  export JAVA_HOME
fi
./gradlew :shared:embedAndSignAppleFrameworkForXcode
