#!/usr/bin/env bash
# Merge release sources into the EXISTING Git checkout, leaving .git/.build untouched.
set -o pipefail
set -u
TARGET="${1:-/home/dice/Git/YrellDevstudioSearch}"
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -d "$TARGET/.git" && ! -f "$TARGET/.git" ]]; then
  echo "Refusing to update $TARGET: existing Git repository not found." >&2
  exit 2
fi
if ! command -v rsync >/dev/null 2>&1; then echo 'rsync is required.' >&2; exit 2; fi
mkdir -p "$TARGET/source/se.yrell.search" "$TARGET/scripts" "$TARGET/tests" "$TARGET/install" || exit 1
rsync -a --delete "$HERE/source/se.yrell.search/" "$TARGET/source/se.yrell.search/" || exit 1
rsync -a --delete "$HERE/scripts/" "$TARGET/scripts/" || exit 1
rsync -a --delete "$HERE/tests/" "$TARGET/tests/" || exit 1
rm -f "$TARGET"/install/se.yrell.search_*.jar
rsync -a "$HERE/install/se.yrell.search_0.2.1.jar" "$TARGET/install/" || exit 1
cp "$HERE/README.md" "$HERE/CHANGELOG.md" "$TARGET/" || exit 1
cat > "$TARGET/.gitignore" <<'IGNORE'
.build/
*.class
*.log
IGNORE
echo "Updated source and JAR in $TARGET; Git metadata and .build were preserved."
