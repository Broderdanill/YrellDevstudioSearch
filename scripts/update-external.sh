#!/usr/bin/env bash
# Safe source+build upgrade from a downloaded release; no BMC binaries are distributed.
set -o pipefail
set -u
TARGET="${1:-/home/dice/Git/YrellDevstudioSearch}"
BMC="${2:-}"
DL="${HOME}/Downloads"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ZIP="$SCRIPT_DIR/YrellDevstudioSearch-0.2.3-FullSource.zip"
if [[ ! -f "$ZIP" ]]; then echo "Missing $ZIP (save ZIP alongside this script)." >&2; exit 2; fi
if [[ ! -d "$TARGET/.git" && ! -f "$TARGET/.git" ]]; then
  echo "Expected existing Git repository: $TARGET" >&2; exit 2
fi
for cmd in unzip tar rsync javac jar python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then echo "Missing required tool: $cmd" >&2; exit 2; fi
done
# Preflight the SDK BEFORE changing the user's repository.
if [[ -z "$BMC" ]]; then
  if [[ -f "$DL/componentDeveloperStudioWindows.zip" ]]; then
    BMC="$DL/componentDeveloperStudioWindows.zip"
  elif [[ -f "$DL/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip" ]]; then
    BMC="$DL/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip"
  elif [[ -d "$TARGET/.build/devstudio/x64/plugins" ]]; then
    BMC="$TARGET/.build/devstudio/x64"
  else
    echo 'Developer Studio SDK/ZIP not found; pass it as second argument. Git checkout not changed.' >&2; exit 2
  fi
fi
if [[ ! -f "$BMC" && ! -d "$BMC" ]]; then echo "BMC SDK path not found: $BMC" >&2; exit 2; fi
TMP="$(mktemp -d)" || exit 1
trap 'rm -rf "$TMP"' EXIT
unzip -q "$ZIP" -d "$TMP" || exit 1
PKG="$TMP/YrellDevstudioSearch-0.2.3"
if [[ ! -f "$PKG/scripts/build.sh" || ! -f "$PKG/source/se.yrell.search/plugin.xml" || ! -f "$PKG/scripts/test-core-offline.sh" ]]; then
  echo 'Unexpected release ZIP structure. Git checkout not changed.' >&2; exit 1
fi
# Test the release in temporary staging first: a build failure must not modify Git sources.
echo '== Offline search/form tests =='
bash "$PKG/scripts/test-core-offline.sh" || exit 1
echo "== Building with local BMC Developer Studio: $BMC =="
bash "$PKG/scripts/build.sh" "$BMC" || exit 1
if [[ -f "$BMC" ]]; then
  bash "$PKG/scripts/test.sh" || exit 1
else
  bash "$PKG/scripts/test.sh" "$BMC" || exit 1
fi
if [[ ! -s "$PKG/install/se.yrell.search_0.2.3.jar" ]]; then
  echo 'Build did not produce expected 0.2.3 JAR. Git checkout not changed.' >&2; exit 1
fi
mkdir -p "$DL" || exit 1
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$DL/YrellDevstudioSearch-backup-${STAMP}"
mkdir -p "$BACKUP" || exit 1
tar -C "$TARGET" --exclude='./.git' --exclude='./.build' -cf "$BACKUP/project-before-update.tar" . || exit 1
echo "Backup: $BACKUP/project-before-update.tar"
bash "$PKG/scripts/install-to-git.sh" "$TARGET" || exit 1
cp "$PKG/install/se.yrell.search_0.2.3.jar" "$DL/" || exit 1
printf '\nUpdated: %s\nJAR: %s\n' "$TARGET" "$DL/se.yrell.search_0.2.3.jar"
if command -v git >/dev/null 2>&1; then
  echo 'Git status:'
  git -C "$TARGET" status --short | head -70
fi
