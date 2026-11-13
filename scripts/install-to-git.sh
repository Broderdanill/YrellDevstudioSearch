#!/usr/bin/env bash
# Merge updated source into an existing Git checkout; never touch .git or cached .build.
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
cp "$HERE/README.md" "$HERE/CHANGELOG.md" "$TARGET/" || exit 1
cat > "$TARGET/.gitignore" <<'IGNORE'
.build/
*.class
*.log
IGNORE
if [[ -f "$HERE/install/se.yrell.search_0.2.3.jar" ]]; then
  # Discard obsolete JAR releases only after the new release has been built.
  rm -f "$TARGET"/install/se.yrell.search_*.jar || exit 1
  cp "$HERE/install/se.yrell.search_0.2.3.jar" "$TARGET/install/" || exit 1
  echo "Updated source and compiled 0.2.3 JAR: $TARGET"
else
  echo "Updated source: $TARGET. Build against your BMC Developer Studio libraries to create the 0.2.3 JAR."
fi
