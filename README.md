# Yrell Devstudio Search 0.1.0

Separate Eclipse/OSGi plugin ID `se.yrell.search`, designed for BMC Developer Studio 26.1.1.
Target local Git repository: `/home/dice/Git/YrellDevstudioSearch`.

## Project layout

- `source/se.yrell.search/` – Eclipse plugin (plugin.xml, OSGi manifest, Java source)
- `install/se.yrell.search_0.1.0.jar` – compiled plugin, **no BMC dependencies bundled**
- `scripts/build.sh` – builds Java 17 JAR against your own Developer Studio ZIP or x64 directory
- `scripts/test.sh` – regression checks for search model and package
- `tests/` – portable JVM regression tests
- `scripts/install-to-git.sh` – merge this source package into target Git working tree

## Build

```bash
cd /home/dice/Git/YrellDevstudioSearch
bash scripts/build.sh /home/dice/Downloads/componentDeveloperStudioWindows.zip
bash scripts/test.sh
```

## Installation on Windows

Close Developer Studio. Copy `install/se.yrell.search_0.1.0.jar` to the Developer
Studio `plugins/` directory. Do not remove BMC plugins. Restart Developer Studio
with `-clean` on the first launch and then open:
**Window → Show View → Other... → Yrell Search → Yrell Search**.

No `-javaagent`, no changes to JVM or server, and Yrell Developer Tools is not required.

## Compatibility note

If Yrell Developer Tools Fast Object List is configured to filter customization
types, disable its filtering for a comprehensive index and rebuild the search index.
Results are limited to objects that the selected BMC provider returns and that the
user has permission to read.

## First test

1. Connect to a **test** AR Server in Developer Studio.
2. Open the Yrell Search view; click `Servers` if the connection is not listed.
3. Optionally enter a narrow name prefilter and click `Build / Refresh index`.
4. Watch progress in Eclipse Progress and wait until it is complete.
5. Search for a known process text or field ID, inspect Matches / Objects tabs.
6. Double-click to open a workflow, then select a few objects and use `Export selected...`.
7. Validate the DEF file in a separate test environment.

See `source/se.yrell.search/README.md` for boundaries. This is an experimental first
release: compile tests are not a substitute for BMC Windows integration testing.
