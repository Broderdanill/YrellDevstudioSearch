#!/usr/bin/env bash
# Install/update *source* to the specified local Git checkout, preserving .git.
set -o pipefail
set -u
TARGET="${1:-/home/dice/Git/YrellDevstudioSearch}"
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -d "$TARGET/.git" && ! -f "$TARGET/.git" ]]; then
  echo "Expected an existing Git repository at $TARGET; refusing to overwrite an unrelated directory." >&2
  exit 2
fi
if [[ ! -w "$TARGET" ]]; then echo "Cannot write to $TARGET" >&2; exit 2; fi
if ! command -v rsync >/dev/null 2>&1; then echo 'rsync is required.' >&2; exit 2; fi
mkdir -p "$TARGET/source" "$TARGET/scripts" "$TARGET/tests" "$TARGET/install"
rsync -a "$HERE/source/se.yrell.search/" "$TARGET/source/se.yrell.search/"
rsync -a "$HERE/scripts/" "$TARGET/scripts/"
rsync -a "$HERE/tests/" "$TARGET/tests/"
rsync -a "$HERE/install/" "$TARGET/install/"
cp "$HERE/README.md" "$HERE/CHANGELOG.md" "$TARGET/"
cat > "$TARGET/.gitignore" <<'IGNORE'
.build/
*.class
*.log
IGNORE
printf 'Updated Git working tree (without touching .git): %s\n' "$TARGET"
printf 'Build: cd %s && bash scripts/build.sh /home/dice/Downloads/componentDeveloperStudioWindows.zip && bash scripts/test.sh\n' "$TARGET"
