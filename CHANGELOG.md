# Changelog

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
