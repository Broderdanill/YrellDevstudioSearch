# Changelog

## 0.2.3 (2026-11-13)

- Fixed the BMC SDK compilation failure in `FormIndexBuilderOfflineTest` by replacing concrete mock implementations of evolving BMC/Eclipse interfaces with dynamic proxies. The test now compiles against both local offline stubs and the 26.1.1 SDK interface contract without implementing SDK-specific abstract methods.
- Corrected offline `IStore.getSupportedTypes()` to use `Set<IModelType>` and `ItemList` usage to use `addItem()` (the same API used in the existing Developer Studio tests).
- Hardened the offline stub interfaces to include abstract methods reported by the real SDK (`getObjectType`, `update`, `getProperty`, `setTaskName`); tests now pass against these stricter fixtures.
- Fixed JAR bytecode and contents checks to avoid premature `grep -q` termination with `pipefail` enabled.
- Preserved all 0.2.2 form, field, workflow, connection and export functionality; no UI or persisted search format change.
- Safe-update path still builds and executes tests against local BMC JARs **before** changing the Git checkout. SDK-backed testing remains required on the target machine.

## 0.2.2 (2026-11-13)

- Index Forms as first-class objects alongside five classic workflow object types.
- Extract form field IDs, names, labels, data types, field types, menu and other optional metadata with BMC public metadata adapter; field enumeration failure produces PARTIAL rather than silently false-negative coverage.
- Add precise `fieldid:`, `fieldname:`, `label:`, `datatype:`, `fieldtype:`, `menu:`, `view:` query tokens and update scope/mode controls.
- Resolve workflow form references to indexed Forms in the Connections graph, preserving unresolved links when no form is indexed.
- Never reuse form-field metadata merely from unchanged form timestamps on incremental refresh.
- Add regression tests for exact field IDs, field metadata matching, parent-form deduplication, form references, hidden-field-capable API retrieval and incomplete field catalogs.
- Provide an SDK-preflight, build/test-first upgrade script with Git source backup. The release ZIP intentionally contains source instead of an unvalidated JAR.

## 0.2.1 (2026-11-13)

- Add **Copy Object Name(s)** to the context menu in all three result tabs, plus a footer action. Deduplicate repeated hits and separate selected names with newlines.
- Add **Open on Server** sub-menu of other connected AR servers (with account and overlay). Resolve selected workflow definitions by exact type/name on the target, in an Eclipse Progress Job. Never reuse source `IModelItem` for a different server; clearly report missing, unsupported and inaccessible items.
- Add a compact visual relationship overview with incoming / selected / outgoing layout. Preserve the exact clickable Connections table beneath the overview.
- Preserve original indexing, query, and DEF export functionality. Add focused regression tests for clipboard payload and exact cross-server matching.

## 0.2.0 (2026-11-13)

- Major workflow search expansion built on the functioning 0.1.0 structure, remaining independent of Yrell Developer Tools.
- Select one/many workflow types (Filters, Active Links, Escalations, Filter Guides, Active Link Guides) without rebuilding index.
- Search expressions: case-insensitive terms, quotes, AND, OR, `-exclude`, `form:`, `field:`, `action:`, `event:`, `guide:`, `name:`, `type:`, `process:`, `where:`, `branch:` and others.
- Extract more action types, If/Else paths and nested BMC API value properties, with safe traversal caps.
- Add a conservative reference graph in a simple Connections tab, with possible event deliveries, guide calls/steps, and unresolved form references; no false claims of runtime execution.
- Fix 0.1.0's export truncation: full matching object set remains available when occurrence table is limited.
- Add Export all matches, selected native BMC opening/DEF export, and detailed match inspector.
- Use SWT virtual results tables and Eclipse background search/index jobs. Serialize index jobs to reduce AR API contention.
- Add optional incremental refresh using BMC object last-modified metadata and a mandatory complete baseline.
- Add per-type index counts, explicit partial/prefiltered warnings, index cancellation preserving prior index.
- Expand headless regression tests against BMC Developer Studio 26.1.1 Java libraries.

## 0.1.0 (2026-11-12)

- First standalone Yrell Search plugin, experimental workflow indexing and native open/export integration.
