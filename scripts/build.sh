#!/usr/bin/env bash
# Yrell Devstudio Search 0.2.3; Java 17; BMC Developer Studio 26.1.1.
set -o pipefail
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVE="${1:-}"
BUILD="$ROOT/.build"
if [[ -f "$ARCHIVE" ]]; then
  PLUGINS="$BUILD/devstudio/x64/plugins"
  if [[ ! -f "$PLUGINS/com.bmc.arsys.studio.ui_26.1.1.build001.jar" ]]; then
    mkdir -p "$BUILD/devstudio"
    echo 'Extracting BMC Developer Studio libraries (local build only)...'
    unzip -q "$ARCHIVE" 'x64/plugins/*' -d "$BUILD/devstudio" || exit 1
  fi
elif [[ -d "$ARCHIVE/x64/plugins" ]]; then PLUGINS="$ARCHIVE/x64/plugins"
elif [[ -d "$ARCHIVE/plugins" ]]; then PLUGINS="$ARCHIVE/plugins"
elif [[ -d "$ARCHIVE" ]] && find "$ARCHIVE" -maxdepth 1 -name 'com.bmc.arsys.studio.ui_*.jar' -print -quit | grep -q .; then PLUGINS="$ARCHIVE"
else
  echo 'Usage: bash scripts/build.sh /path/to/componentDeveloperStudioWindows.zip (or Developer Studio x64 directory)' >&2
  exit 2
fi
if [[ ! -f "$PLUGINS/com.bmc.arsys.studio.ui_26.1.1.build001.jar" ]]; then
  echo 'WARNING: expected Developer Studio 26.1.1 build001 libraries are missing; compatibility not verified.' >&2
fi
mkdir -p "$BUILD" "$ROOT/install"
find "$ROOT/source/se.yrell.search/src" -name '*.java' | sort > "$BUILD/sources.txt"
CP="$(find -L "$PLUGINS" -name '*.jar' | sort | paste -sd: -)"
rm -rf "$BUILD/classes" "$BUILD/package"
mkdir -p "$BUILD/classes" "$BUILD/package"
echo "Compiling $(wc -l < "$BUILD/sources.txt") Java sources..."
javac --release 17 -proc:none -encoding UTF-8 -cp "$CP" -d "$BUILD/classes" @"$BUILD/sources.txt" || exit 1
cp -a "$BUILD/classes"/. "$BUILD/package"/
cp "$ROOT/source/se.yrell.search/plugin.xml" "$BUILD/package/"
cp "$ROOT/source/se.yrell.search/README.md" "$BUILD/package/"
rm -f "$ROOT"/install/se.yrell.search_*.jar
jar --create --file "$ROOT/install/se.yrell.search_0.2.3.jar" --manifest "$ROOT/source/se.yrell.search/META-INF/MANIFEST.MF" -C "$BUILD/package" . || exit 1
printf 'Output: %s\n' "$ROOT/install/se.yrell.search_0.2.3.jar"
