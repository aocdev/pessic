#!/usr/bin/env bash
# Builds Pessic installers with jpackage for the system it runs on. Each installer carries its own
# Java runtime (only the modules Pessic needs), so users do not need Java.
#
#   packaging/package.sh                  default types for this system
#   packaging/package.sh rpm deb          Linux: .rpm (needs rpmbuild), .deb (needs dpkg-deb, fakeroot)
#   packaging/package.sh msi              Windows: .msi (needs WiX 3, in Git Bash)
#   packaging/package.sh dmg              macOS: .dmg
#   packaging/package.sh app-image        any system: the app folder without an installer (for testing)
#
# Output: dist/. Set SKIP_BUILD=1 to reuse the jars from a previous `mvn install`.
# Optional: PESSIC_DEB_MAINTAINER="Name <email>" for the .deb's Maintainer field.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ------------------------------------------------------------------ settings
NAME="Pessic"
PACKAGE="pessic"
VENDOR="Pessic"
DESCRIPTION="Pressupost mensual: un pessic de cada despesa, al seu lloc"
MAIN_CLASS="cat.pessic.app.Launcher"
# Never change it: Windows uses it to recognise new versions of the same app and upgrade in place.
WIN_UPGRADE_UUID="52318f3f-4efd-4c67-a941-648d51f7bfc2"
# What `jdeps` reports for the app (java.base, java.desktop, java.sql) plus what its libraries need
# (SQLite, Jackson), jdk.unsupported for JavaFX and jdk.localedata for Catalan month and day names.
MODULES="java.base,java.desktop,java.sql,java.logging,java.xml,java.naming,jdk.unsupported,jdk.localedata,javafx.controls"
JLINK_OPTIONS="--strip-debug --no-man-pages --no-header-files --strip-native-commands --include-locales=ca,es,en"

VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
APP_VERSION="${VERSION%-SNAPSHOT}"
YEAR="$(date +%Y)"
# the project's Maven Wrapper (same Maven everywhere), or Maven if the wrapper is missing
MVN="mvn"; [ -x "$ROOT/mvnw" ] && MVN="$ROOT/mvnw"

case "$(uname -s)" in
  Linux*)   OS=linux ;;
  Darwin*)  OS=mac ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  *) echo "Unknown system: $(uname -s)" >&2; exit 1 ;;
esac

TYPES=()
for t in "$@"; do TYPES+=("$t"); done
if [ $# -eq 0 ]; then
  case "$OS" in
    linux)
      command -v rpmbuild >/dev/null && TYPES+=(rpm)
      command -v dpkg-deb >/dev/null && TYPES+=(deb)
      [ -n "${TYPES[*]:-}" ] || TYPES=(app-image) ;;
    mac) TYPES=(dmg) ;;
    windows) TYPES=(msi) ;;
  esac
fi

# ------------------------------------------------------------------ build and stage the jars
if [ "${SKIP_BUILD:-}" != "1" ]; then
  "$MVN" -B -q -DskipTests install
fi

STAGE="$ROOT/pessic-app/target/jpackage"
rm -rf "$STAGE" && mkdir -p "$STAGE/input" "$STAGE/javafx" "$STAGE/deps"
"$MVN" -B -q -f pessic-app/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$STAGE/deps"
for jar in "$STAGE"/deps/*.jar; do
  base="$(basename "$jar")"
  if [[ "$base" =~ ^javafx-[a-z]+-[0-9.]+\.jar$ ]]; then
    continue                                  # empty, platform-independent JavaFX jar: not needed
  elif [[ "$base" == javafx-* ]]; then
    cp "$jar" "$STAGE/javafx/"                # JavaFX with this system's native code: linked as modules
  else
    cp "$jar" "$STAGE/input/"                 # everything else runs from the class path
  fi
done
cp "pessic-app/target/pessic-app-$VERSION.jar" "$STAGE/input/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$STAGE/input/"   # Apache 2.0: the NOTICE goes with every copy

case "$OS" in
  linux) ICON="$ROOT/packaging/icons/pessic.png" ;;
  mac) ICON="$ROOT/packaging/icons/pessic.icns" ;;
  windows) ICON="$ROOT/packaging/icons/pessic.ico" ;;
esac

COMMON=(
  --name "$NAME"
  --app-version "$APP_VERSION"
  --vendor "$VENDOR"
  --description "$DESCRIPTION"
  --copyright "© $YEAR $VENDOR"
  --icon "$ICON"
  --input "$STAGE/input"
  --main-jar "pessic-app-$VERSION.jar"
  --main-class "$MAIN_CLASS"
  --module-path "$STAGE/javafx"
  --add-modules "$MODULES"
  --jlink-options "$JLINK_OPTIONS"
  --dest "$ROOT/dist"
)

mkdir -p "$ROOT/dist"
for type in "${TYPES[@]}"; do
  echo "==> $NAME $APP_VERSION: $type"
  EXTRA=()
  [ "$type" != app-image ] && EXTRA=(--license-file "$ROOT/LICENSE")  # shown by the Windows and macOS installers
  case "$type" in
    rpm|deb)
      EXTRA+=(--linux-package-name "$PACKAGE" --linux-shortcut --linux-menu-group "Office;Finance;"
             --linux-rpm-license-type "Apache-2.0")
      if [ "$type" = deb ] && [ -n "${PESSIC_DEB_MAINTAINER:-}" ]; then
        EXTRA+=(--linux-deb-maintainer "$PESSIC_DEB_MAINTAINER")
      fi ;;
    msi)
      EXTRA+=(--win-menu --win-menu-group "$NAME" --win-shortcut --win-dir-chooser --win-per-user-install
             --win-upgrade-uuid "$WIN_UPGRADE_UUID") ;;
    dmg|pkg)
      EXTRA+=(--mac-package-identifier "cat.pessic.app" --mac-package-name "$NAME") ;;
    app-image) ;;
    *) echo "Unknown type: $type" >&2; exit 1 ;;
  esac
  [ "$type" = app-image ] && rm -rf "$ROOT/dist/$NAME" "$ROOT/dist/$NAME.app"
  jpackage --type "$type" "${COMMON[@]}" ${EXTRA[@]+"${EXTRA[@]}"}  # (bash 3.2 on macOS: empty arrays)
done

echo "==> Done:"
ls -la "$ROOT/dist"
