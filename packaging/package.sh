#!/usr/bin/env bash
#
# Builds a portable Fakturomost desktop app for the current OS with jpackage:
#   Windows: target/dist/Fakturomost-<version>-windows-x64.zip  (Fakturomost.exe + bundled Java)
#   Linux:   target/dist/Fakturomost-<version>-linux-x64.tar.gz (bin/Fakturomost + bundled Java)
#
# jpackage only builds for the OS it runs on, so CI runs this once per OS
# (.github/workflows/package.yml). Locally:
#
#   ./mvnw -B -ntp -pl fakturomost-desktop -am package -DskipTests
#   bash packaging/package.sh
#
# Needs a full JDK 21+ (jpackage is in $JAVA_HOME/bin) — run from the repository root.
# Git Bash works on Windows. Archives are written under fakturomost-desktop/target/dist/.
#
# FAKTUROMOST_INPUT_DIR overrides the jar folder (default: the one Maven fills), for testing
# the packaging step on its own.

set -euo pipefail

case "$(uname -s)" in
    Linux*)               OS=linux ;;
    MINGW*|MSYS*|CYGWIN*) OS=windows ;;
    *) echo "Unsupported OS $(uname -s): only Windows and Linux are packaged for now." >&2; exit 1 ;;
esac
ARCH=x64

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$OS" == windows ]]; then
    # D:/a/... instead of /d/a/...: jpackage.exe is a Windows program, and Git Bash does not
    # reliably convert paths inside arguments like --add-launcher name=/path.
    ROOT="$(cd "$ROOT" && pwd -W)"
fi
DESKTOP="$ROOT/fakturomost-desktop"
TARGET="$DESKTOP/target"
INPUT="${FAKTUROMOST_INPUT_DIR:-$TARGET/jpackage-input}"
DIST="$TARGET/dist"
APP_NAME="Fakturomost"
MAIN_CLASS="dev.michalraczkowski.fakturomost.desktop.Launcher"

EXE=""
[[ "$OS" == windows ]] && EXE=".exe"
JPACKAGE="jpackage"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage$EXE" ]]; then
    JPACKAGE="$JAVA_HOME/bin/jpackage$EXE"
fi
command -v "$JPACKAGE" >/dev/null || { echo "jpackage not found: install a full JDK 21+ and set JAVA_HOME." >&2; exit 1; }

# --- Version: from the jar Maven built (pom.properties), e.g. 0.1.0-SNAPSHOT ------------------
POM_PROPS="$TARGET/maven-archiver/pom.properties"
[[ -f "$POM_PROPS" ]] || { echo "Build first: ./mvnw -pl fakturomost-desktop -am package -DskipTests" >&2; exit 1; }
VERSION="$(sed -n 's/^version=//p' "$POM_PROPS" | tr -d '\r')"
# jpackage wants a plain numeric version (Windows: up to 3 numbers), so 0.1.0-SNAPSHOT -> 0.1.0
APP_VERSION="$(echo "$VERSION" | grep -oE '^[0-9]+(\.[0-9]+){0,2}')"
MAIN_JAR="fakturomost-desktop-$VERSION.jar"

[[ -f "$TARGET/$MAIN_JAR" ]] || { echo "Missing $TARGET/$MAIN_JAR — run the Maven package step first." >&2; exit 1; }
[[ -d "$INPUT" ]] || { echo "Missing $INPUT — the Maven package step copies the runtime jars there." >&2; exit 1; }
cp "$TARGET/$MAIN_JAR" "$INPUT/"

echo "Packaging $APP_NAME $VERSION (app version $APP_VERSION) for $OS-$ARCH"
rm -rf "$DIST"
mkdir -p "$DIST"

COMMON=(
    --type app-image
    --name "$APP_NAME"
    --app-version "$APP_VERSION"
    --vendor "Fakturomost open-source project"
    --description "Import, browse and export KSeF FA(3) e-invoices (experimental)"
    --copyright "Apache License 2.0"
    --input "$INPUT"
    --main-jar "$MAIN_JAR"
    --main-class "$MAIN_CLASS"
    --dest "$DIST"
    # Pin what the OS would otherwise guess (lesson from M3): UTF-8 everywhere, JavaFX's own scaling.
    --java-options "-Dfile.encoding=UTF-8"
    --java-options "-Dstdout.encoding=UTF-8"
)

if [[ "$OS" == windows ]]; then
    # A second launcher that keeps a console window open, so startup errors are visible.
    "$JPACKAGE" "${COMMON[@]}" \
        --icon "$ROOT/packaging/icons/fakturomost.ico" \
        --add-launcher "$APP_NAME-console=$ROOT/packaging/windows-console-launcher.properties"
else
    "$JPACKAGE" "${COMMON[@]}" \
        --icon "$ROOT/packaging/icons/fakturomost.png"
fi

APP_DIR="$DIST/$APP_NAME"
[[ -d "$APP_DIR" ]] || { echo "jpackage did not create $APP_DIR" >&2; exit 1; }

# --- Extras next to the program: demo data, licences, a short readme ----------------------------
cp -R "$ROOT/demo-data" "$APP_DIR/demo-data"
cp "$ROOT/LICENSE" "$APP_DIR/LICENSE.txt"
cp "$ROOT/packaging/README-app.txt" "$APP_DIR/README.txt"
cp "$ROOT/fakturomost-desktop/src/main/resources/dev/michalraczkowski/fakturomost/desktop/ui/fonts/OFL-IBM-Plex.txt" \
   "$APP_DIR/LICENSE-IBM-Plex-font.txt"

# --- Archive ------------------------------------------------------------------------------------
BASE="$APP_NAME-$VERSION-$OS-$ARCH"
if [[ "$OS" == windows ]]; then
    ARCHIVE="$DIST/$BASE.zip"
    (cd "$DIST" && powershell.exe -NoProfile -NonInteractive -Command \
        "Compress-Archive -Path '$APP_NAME' -DestinationPath '$BASE.zip' -CompressionLevel Optimal")
else
    ARCHIVE="$DIST/$BASE.tar.gz"
    tar -C "$DIST" -czf "$ARCHIVE" "$APP_NAME" # tar keeps the executable bit; a zip would lose it
fi

echo "Done: $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1))"
