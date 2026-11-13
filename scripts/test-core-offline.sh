#!/usr/bin/env bash
# API-independent regression tests for form field extraction, search and reference graph.
# These stubs are TEST ONLY and are NEVER packaged into se.yrell.search JAR.
set -o pipefail
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.build/offline-tests"
rm -rf "$OUT"
mkdir -p "$OUT"
find "$ROOT/tests/offline-stubs" -name '*.java' -print > "$OUT/sources.txt"
printf '%s\n' "$ROOT/source/se.yrell.search/src/se/yrell/search/core/SearchIndex.java" "$ROOT/source/se.yrell.search/src/se/yrell/search/core/SearchQuery.java" "$ROOT/source/se.yrell.search/src/se/yrell/search/core/ReferenceGraph.java" "$ROOT/source/se.yrell.search/src/se/yrell/search/bmc/FormExtractor.java" "$ROOT/source/se.yrell.search/src/se/yrell/search/bmc/IndexBuilder.java" "$ROOT/tests/FormSearchTest.java" "$ROOT/tests/FormIndexBuilderOfflineTest.java" "$ROOT/tests/SearchIndexTest.java" "$ROOT/tests/SearchQueryTest.java" "$ROOT/tests/ReferenceGraphTest.java" >> "$OUT/sources.txt"
javac --release 17 -encoding UTF-8 -d "$OUT" @"$OUT/sources.txt" || exit 1
for cls in FormSearchTest FormIndexBuilderOfflineTest SearchIndexTest SearchQueryTest ReferenceGraphTest; do
  java -cp "$OUT" "$cls" || exit 1
done
echo 'CORE OFFLINE REGRESSION TESTS PASSED'
