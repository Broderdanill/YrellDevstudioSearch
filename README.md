# Yrell Devstudio Search 0.2.1

Standalone Eclipse/OSGi plugin `se.yrell.search`, built against BMC Developer Studio **26.1.1 build001** (Java 17).
Target Git repository: `/home/dice/Git/YrellDevstudioSearch`.

## What's new in 0.2.1

- **Copy Object Name(s):** right-click an entry in Matches, Objects or Connections to copy one or several selected unique object names, one per line. A **Copy names** button also appears in the footer.
- **Open on Server → [connected server]:** right-click selected workflow objects, pick another connected AR server (displayed with account and overlay), and resolve the **exact object type and name** there. It runs as an Eclipse Progress Job. Missing objects are reported; the plugin never falls back to opening a source-server item.
- **Visual Connections map:** a compact read-only overview above the exact Connections list shows incoming relations, the selected object and outgoing relations, with three labels per side and a +N indicator for busy objects.
- Native BMC opening and DEF export are preserved. No Java agent, external library or BMC binaries are bundled.

### Open on Server – boundaries

1. The context menu lists other **currently connected AR servers**. Destination identity is determined by exact object type and exact name, scoped to the destination account and overlay.
2. Nothing is copied or deployed to the destination, and the source index is not changed.
3. Uses the destination's BMC `IStore.getItemList(type,names)` name-scoped lookup. Missing, unsupported and inaccessible items are reported, not silently substituted. Some BMC providers may not support this lookup.
4. Multi-selection is supported. Existing target objects open even if others were not found, with a summary of all unavailable objects. The lookup runs in Eclipse Progress rather than on the SWT UI thread.
5. **Export always remains from the indexed source server.** Cross-server opening is not a comparison tool; two identically named objects may have different definitions.
6. UI behavior, live AR-server resolution and BMC native opening on another server require testing in the Windows installation.

## What's new in 0.2.0

- **Object-type multi-select:** check one or more of Filters, Active Links, Escalations, Filter Guides and Active Link Guides. The selection filters an existing index *without another server query*. **All/None** shortcuts.
- **More complete workflow extraction:** structured If/Else action locations; Run Process, `$PROCESS$` and assignment values; Set Fields and Push Fields mappings, Open Window, Change Field, Call Guide, Service, Message, Notify, qualifications and nested fields. Remaining public AR API action value properties are traversed read-only, with strict bounds.
- **Smarter search:** quoted phrases, field-scoped terms, AND, OR, exclusion. See examples below.
- **Connections tab:** associated/target forms, guide calls/steps and possible event sender→receiver connections with clear confidence. A relation is never represented as proof of runtime execution.
- **Two separate result sets:** exact *Matches* plus deduplicated *Objects*; opening, selected export, and **Export all matches** through BMC's own editor/DEF export APIs. The 2,000-row display cap **does not limit** object selection or export-all.
- **Scalable UI:** background index and search jobs, virtual SWT tables; search and type-filter changes don't invoke AR Server.
- **Refresh changed:** compares BMC last-update timestamps and reuses unchanged indexed definitions when safely verifiable. A complete baseline is required; null/changed timestamps force read again. Full reindex remains available.
- **Index details:** per-type counts, read failures, partial/prefiltered state.

## Typical usage

1. Install `install/se.yrell.search_0.2.1.jar` to the **plugins** folder of Developer Studio on Windows, replacing 0.2.0 (and any older plugin JAR). Start with `-clean` once.
2. Window → Show View → Other → Yrell Search → Yrell Search.
3. Choose the connected AR server and click **Build / Refresh index**. Watch Eclipse Progress. Full indexing may require many AR API calls; there is **no automatic index build at startup**.
4. Check one or more object types under **Search in**. Choose *Everywhere* or a narrower content mode, then search.
5. Inspect matches and action positions. Switch to *Objects* to select/export unique definitions. Right-click a row to **Copy Object Name(s)** or **Open on Server** (multi-select supported). Use *Connections* for a visual overview and an exact list; double-click a resolvable related object to open it.
6. Click **Refresh changed** for subsequent reads, or run full refresh if object timestamps / provider metadata might be stale.

## Query examples

| Query | Meaning |
|---|---|
| `ChildClosed` | Any occurrence containing the text |
| `event:"Child Closed"` | Literal event sender/receiver text |
| `form:"HPD:Help Desk" action:PushFields` | Objects matching BOTH conditions |
| `field:536870913` | Field-ID occurrence within actions or qualifications |
| `guide:"My Guide"` | Guide call/step reference |
| `action:SetFields` | Action type |
| `name:"HPD:INC"` | Workflow object name |
| `where:Else` | Where/branch of occurrence |
| `process:$PROCESS$` | Process-related property/mapping |
| `foo OR bar` | Either term (whole-object semantics) |
| `action:PushFields -form:"Temporary Form"` | Matches first condition, excludes the other |

Whitespace means **AND at the object level**, not necessarily within the same action. Quoted phrases preserve spaces. `type:Filter` matches that exact type; object type checkboxes give a simpler equivalent. Unrecognized `x:y` is treated as plain text to avoid invalidating common form names.

## Project layout

- `source/se.yrell.search/`: OSGi manifest, plugin.xml and Java 17 source code
- `install/`: distributable JAR, **without BMC binaries**
- `scripts/build.sh`: compile against your local BMC Developer Studio ZIP (or extracted x64 tree)
- `scripts/test.sh`: offline model/extractor/graph/index smoke tests
- `scripts/install-to-git.sh`: copy source into an existing Git working tree
- `tests/`: tests, including genuine BMC API value objects

## Build (Ubuntu)

```bash
cd /home/dice/Git/YrellDevstudioSearch
bash scripts/build.sh /home/dice/Downloads/componentDeveloperStudioWindows.zip
bash scripts/test.sh
```

## Important boundaries

- **No Java agent, no native hooks, no server writes and no persistent on-disk search index.** Definitions stay in process memory. Only BMC's own selected-object export writes the requested local DEF file.
- **Saved server definitions**, not unsaved changes in an open editor. Rebuild/refresh after changes.
- **No complete static resolution of dynamic paths**: runtime expressions may compute event names, forms, servers, field names and guide targets. Possible links are labeled as such; unresolvable forms are shown by name rather than fabricated objects.
- **Scope**: classic AR System Active Links, Filters, Escalations and Guides. It does not index Innovation Studio rules/record definitions or all forms/menus as top-level exportable search objects.
- **Completeness**: results cover objects the connected BMC provider returns and allows reading. If another plugin (including Fast Object List) filters the provider or permissions hide definitions, this index cannot detect omitted objects automatically. Temporarily disable provider filtering and use a full rebuild if exhaustive coverage is required.
- **Partial index**: network failures, cancelled jobs, name-prefiltering or inaccessible definitions are surfaced explicitly. The previous good index survives a cancellation.
- **Incremental refresh** depends on reliable BMC modification timestamps. Use **Build / Refresh index** when in doubt.
- **Performance**: no AR API calls on UI thread for indexing/search. Some BMC APIs cannot be interrupted until an in-flight network call completes; a scheduling rule prevents this plugin from overlapping heavy index jobs.
- **Data exposure**: workflow definitions can contain secrets or sensitive values. Search index remains memory-only; do not paste results into logs or share exported DEF without review.
- Windows SWT UI and real AR server indexing/export still require integration verification on the target machine; these are not validated by headless Java tests.
