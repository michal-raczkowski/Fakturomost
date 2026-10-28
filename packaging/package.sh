#!/usr/bin/env bash
#
# Builds a portable Fakturomost desktop app for the current OS with jpackage:
#   Windows: target/dist/Fakturomost-<version>-windows-x64.zip       (Fakturomost.exe + bundled Java)
#   Linux:   target/dist/Fakturomost-<version>-linux-x64.AppImage    (one file, bundled Java)
#            target/dist/Fakturomost-<version>-linux-x64.tar.gz      (same app as a folder, with demo-data)
#
# jpackage only builds for the OS it runs on, so CI runs this once per OS
# (.github/workflows/package.yml). Locally, from the repository root:
#
#   bash packaging/package.sh             # clean build of the desktop app, then package it
#   bash packaging/package.sh --no-build  # package what the last Maven build left in target/
#
# Needs a full JDK 21+ (jpackage and jar are in $JAVA_HOME/bin). Git Bash works on Windows.
# Archives are written under fakturomost-desktop/target/dist/.
#
# The jars are staged into a fresh folder on every run, and the packaged stylesheet is compared
# with the one in src/: a jar left over from an older build can no longer end up in the package.
#
# Linux AppImage: appimagetool and the AppImage runtime are downloaded once (pinned versions,
# SHA-256 checked) into ~/.cache/fakturomost-packaging. FUSE is not needed to build.
#
# FAKTUROMOST_INPUT_DIR overrides the runtime-jar folder (default: the one Maven fills), for
# testing the packaging step on its own; it implies --no-build.

set -euo pipefail

BUILD=true
for arg in "$@"; do
    case "$arg" in
        --no-build) BUILD=false ;;
        -h|--help) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $arg (use --no-build or --help)" >&2; exit 1 ;;
    esac
done
[[ -n "${FAKTUROMOST_INPUT_DIR:-}" ]] && BUILD=false

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
STAGE="$TARGET/jpackage-stage"
DIST="$TARGET/dist"
APP_NAME="Fakturomost"
MAIN_CLASS="dev.michalraczkowski.fakturomost.desktop.Launcher"
CSS_PATH="dev/michalraczkowski/fakturomost/desktop/ui/fakturomost.css"

EXE=""
[[ "$OS" == windows ]] && EXE=".exe"
jdk_tool() {
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/$1$EXE" ]]; then
        echo "$JAVA_HOME/bin/$1$EXE"
    else
        command -v "$1" || { echo "$1 not found: install a full JDK 21+ and set JAVA_HOME." >&2; exit 1; }
    fi
}
JPACKAGE="$(jdk_tool jpackage)"
JAR="$(jdk_tool jar)"

# --- Build: always from a clean tree, so the package matches the sources --------------------------
if [[ "$BUILD" == true ]]; then
    echo "Building the desktop app (clean package, tests skipped — run ./mvnw verify for those)"
    (cd "$ROOT" && ./mvnw -B -ntp -q -pl fakturomost-desktop -am clean package -DskipTests)
fi

# --- Version: from the jar Maven built (pom.properties), e.g. 0.1.0-SNAPSHOT ------------------
POM_PROPS="$TARGET/maven-archiver/pom.properties"
[[ -f "$POM_PROPS" ]] || { echo "Build first: bash packaging/package.sh (without --no-build)" >&2; exit 1; }
VERSION="$(sed -n 's/^version=//p' "$POM_PROPS" | tr -d '\r')"
# jpackage wants a plain numeric version (Windows: up to 3 numbers), so 0.1.0-SNAPSHOT -> 0.1.0
APP_VERSION="$(echo "$VERSION" | grep -oE '^[0-9]+(\.[0-9]+){0,2}')"
MAIN_JAR="fakturomost-desktop-$VERSION.jar"

[[ -f "$TARGET/$MAIN_JAR" ]] || { echo "Missing $TARGET/$MAIN_JAR — build first." >&2; exit 1; }
[[ -d "$INPUT" ]] || { echo "Missing $INPUT — the Maven package step copies the runtime jars there." >&2; exit 1; }

# --- Guard against packaging an old UI -------------------------------------------------------------
# The stylesheet inside the jar must be the one in src/. If it is not, the jar predates the
# sources (e.g. --no-build after a pull) and the package would show the old look.
CHECK="$TARGET/jpackage-check"
rm -rf "$CHECK" && mkdir -p "$CHECK"
(cd "$CHECK" && "$JAR" xf "$TARGET/$MAIN_JAR" "$CSS_PATH")
if ! cmp -s "$CHECK/$CSS_PATH" "$DESKTOP/src/main/resources/$CSS_PATH"; then
    echo "$MAIN_JAR is older than the sources (its fakturomost.css differs from src/)." >&2
    echo "Run without --no-build, or rebuild: ./mvnw -pl fakturomost-desktop -am clean package -DskipTests" >&2
    exit 1
fi
rm -rf "$CHECK"

# --- Stage: a fresh folder with exactly the runtime jars + the main jar ------------------------
# jpackage puts every jar in --input on the class path, so a stale fakturomost-*.jar left in
# jpackage-input by an earlier build could shadow the new classes and stylesheet.
rm -rf "$STAGE" && mkdir -p "$STAGE"
for jar in "$INPUT"/*.jar; do
    name="$(basename "$jar")"
    case "$name" in
        fakturomost-desktop-*.jar) continue ;; # only the freshly built main jar, copied below
        fakturomost-*-"$VERSION".jar) cp "$jar" "$STAGE/" ;;
        fakturomost-*.jar) echo "Skipping $name (not version $VERSION — left over from an older build)" ;;
        *) cp "$jar" "$STAGE/" ;;
    esac
done
cp "$TARGET/$MAIN_JAR" "$STAGE/"

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
    --input "$STAGE"
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
cp "$DESKTOP/src/main/resources/dev/michalraczkowski/fakturomost/desktop/ui/fonts/OFL-IBM-Plex.txt" \
   "$APP_DIR/LICENSE-IBM-Plex-font.txt"

BASE="$APP_NAME-$VERSION-$OS-$ARCH"

# --- Linux: AppImage ---------------------------------------------------------------------------
# One executable file. The jpackage app image goes into an AppDir as it is (its launcher finds
# lib/ next to itself), with AppRun, a .desktop entry and the icon at the top level.
build_appimage() {
    local tools="${XDG_CACHE_HOME:-$HOME/.cache}/fakturomost-packaging"
    local tool="$tools/appimagetool-1.9.1-x86_64.AppImage"
    local runtime="$tools/type2-runtime-20251108-x86_64"
    mkdir -p "$tools"
    fetch "$tool" "https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage" \
        ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
    fetch "$runtime" "https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64" \
        2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d
    chmod +x "$tool"

    local appdir="$DIST/$APP_NAME.AppDir"
    rm -rf "$appdir" && mkdir -p "$appdir"
    cp -R "$APP_DIR" "$appdir/$APP_NAME"
    cat > "$appdir/AppRun" <<'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/Fakturomost/bin/Fakturomost" "$@"
APPRUN
    chmod +x "$appdir/AppRun"
    cat > "$appdir/fakturomost.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=$APP_NAME
GenericName=KSeF e-invoice viewer
Comment=Import, browse and export KSeF FA(3) e-invoices (experimental)
Exec=Fakturomost %F
Icon=fakturomost
Terminal=false
Categories=Office;Finance;
Keywords=KSeF;FA(3);invoice;faktura;CSV;Excel;
X-AppImage-Version=$VERSION
DESKTOP
    cp "$ROOT/packaging/icons/fakturomost.png" "$appdir/fakturomost.png"
    ln -s fakturomost.png "$appdir/.DirIcon"

    local out="$DIST/$BASE.AppImage"
    # Extract-and-run: build machines (CI runners, containers) often have no FUSE.
    local log
    if ! log="$(APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 "$tool" --no-appstream --runtime-file "$runtime" \
            "$appdir" "$out" 2>&1)"; then
        echo "$log" >&2
        echo "appimagetool failed" >&2
        exit 1
    fi
    chmod +x "$out"
    rm -rf "$appdir"
    echo "Done: $out ($(du -h "$out" | cut -f1))"
}

fetch() { # fetch <file> <url> <sha256>: download once, always verify
    local file="$1" url="$2" sum="$3"
    if [[ ! -f "$file" ]]; then
        echo "Downloading $(basename "$file")"
        curl -fsSL --retry 3 -o "$file.part" "$url"
        mv "$file.part" "$file"
    fi
    echo "$sum  $file" | sha256sum -c --quiet - || { rm -f "$file"; echo "Checksum mismatch: $url" >&2; exit 1; }
}

# --- Archives ----------------------------------------------------------------------------------
if [[ "$OS" == windows ]]; then
    ARCHIVE="$DIST/$BASE.zip"
    (cd "$DIST" && powershell.exe -NoProfile -NonInteractive -Command \
        "Compress-Archive -Path '$APP_NAME' -DestinationPath '$BASE.zip' -CompressionLevel Optimal")
else
    ARCHIVE="$DIST/$BASE.tar.gz"
    tar -C "$DIST" -czf "$ARCHIVE" "$APP_NAME" # tar keeps the executable bit; a zip would lose it
    build_appimage
fi

echo "Done: $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1))"
