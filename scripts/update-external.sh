#!/usr/bin/env bash
# Optional local upgrade helper; also distributed separately as yrell-search-0201-update.sh.
set -o pipefail
set -u
TARGET="${1:-/home/dice/Git/YrellDevstudioSearch}"
BMC="${2:-}"
DL="${HOME}/Downloads"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ZIP="$SCRIPT_DIR/YrellDevstudioSearch-0.2.1-FullSource.zip"
if [[ ! -f "$ZIP" ]]; then echo "Missing $ZIP (save FullSource ZIP alongside script)." >&2; exit 2; fi
if [[ ! -d "$TARGET/.git" && ! -f "$TARGET/.git" ]]; then echo "Expected existing Git repository: $TARGET" >&2; exit 2; fi
for cmd in unzip tar rsync javac jar python3; do
  if ! command -v "$cmd" >/dev/null 2>&1; then echo "Missing required tool: $cmd" >&2; exit 2; fi
done
mkdir -p "$DL" || exit 1
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="$DL/YrellDevstudioSearch-backup-${STAMP}"
mkdir -p "$BACKUP" || exit 1
# Preserve local source and untracked files, do not duplicate .git and potentially huge .build.
tar -C "$TARGET" --exclude='./.git' --exclude='./.build' -cf "$BACKUP/project-before-update.tar" . || exit 1
echo "Backup: $BACKUP/project-before-update.tar"
TMP="$(mktemp -d)" || exit 1
trap 'rm -rf "$TMP"' EXIT
unzip -q "$ZIP" -d "$TMP" || exit 1
PKG="$TMP/YrellDevstudioSearch-0.2.1"
if [[ ! -f "$PKG/scripts/build.sh" || ! -f "$PKG/source/se.yrell.search/plugin.xml" ]]; then
  echo 'Unexpected ZIP structure; leaving Git checkout unchanged.' >&2; exit 1
fi
bash "$PKG/scripts/install-to-git.sh" "$TARGET" || exit 1
if [[ -z "$BMC" ]]; then
  if [[ -f "$DL/componentDeveloperStudioWindows.zip" ]]; then
    BMC="$DL/componentDeveloperStudioWindows.zip"
  elif [[ -f "$DL/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip" ]]; then
    BMC="$DL/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip"
  elif [[ -d "$TARGET/.build/devstudio/x64/plugins" ]]; then
    BMC="$TARGET/.build/devstudio/x64"
  else
    echo 'Cannot locate Developer Studio archive or cached libraries.' >&2; exit 1
  fi
fi
echo "Building against: $BMC"
bash "$TARGET/scripts/build.sh" "$BMC" || exit 1
bash "$TARGET/scripts/test.sh" || exit 1
cp "$TARGET/install/se.yrell.search_0.2.1.jar" "$DL/" || exit 1
echo
printf 'Updated: %s\nJAR: %s\n' "$TARGET" "$DL/se.yrell.search_0.2.1.jar"
if command -v git >/dev/null 2>&1; then
  echo 'Git status:'
  git -C "$TARGET" status --short | head -70
fi
