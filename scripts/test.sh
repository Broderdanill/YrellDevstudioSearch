#!/usr/bin/env bash
set -o pipefail
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/install/se.yrell.search_0.2.1.jar"
if [[ ! -f "$JAR" ]]; then echo 'Build first.' >&2; exit 2; fi
mkdir -p "$ROOT/.build/tests"
STUDIO="${1:-$ROOT/.build/devstudio/x64/plugins}"
if [[ -d "$STUDIO/x64/plugins" ]]; then STUDIO="$STUDIO/x64/plugins"; fi
if [[ -d "$STUDIO/plugins" ]]; then STUDIO="$STUDIO/plugins"; fi
BMC_CP="$(find -L "$STUDIO" -name '*.jar' 2>/dev/null | sort | paste -sd: -)"
CP="$JAR:$ROOT/.build/tests:$BMC_CP"
javac --release 17 -proc:none -cp "$CP" -d "$ROOT/.build/tests" "$ROOT/tests/"*.java || exit 1
for test_file in "$ROOT"/tests/*Test.java; do
  test_name="$(basename "$test_file" .java)"
  java -cp "$CP" "$test_name" || exit 1
done
if ! javap -verbose -classpath "$JAR" se.yrell.search.ui.SearchView | grep -q 'major version: 61'; then
  echo 'Incorrect bytecode version (expected Java 17).' >&2; exit 1
fi
jar tf "$JAR" | grep -q 'se/yrell/search/ui/SearchView.class' || exit 1
jar tf "$JAR" | grep -q '^plugin.xml$' || exit 1
python3 - "$ROOT/source/se.yrell.search/plugin.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
assert root.tag=='plugin'
assert any(e.attrib.get('point') == 'org.eclipse.ui.views' for e in root.findall('extension'))
print('Plugin XML: PASS')
PY
echo 'ALL TESTS PASSED'
