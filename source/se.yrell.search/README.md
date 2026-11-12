# Yrell Devstudio Search 0.1.0

Dockable Eclipse view for BMC Developer Studio 26.1.1 (Java 17).
Independent of Yrell Developer Tools. No Java agent or BMC code modifications.

Select AR server → optionally enter a *name prefilter* → Build / Refresh index.
Search locally by text and mode (ALL / EVENTS / ACTIONS / FIELDS / FORMS / REFERENCES).
Matches tab shows each occurrence. Objects tab deduplicates matches for export.
Multi-select rows and select Open or Export selected... to use BMC's own editor and DEF export.

**Scope/limitations:** indexes saved Active Links, Filters, Escalations, Active Link Guides
and Filter Guides from the selected AR connection. Does not index Forms, Views,
Menus, Innovation Studio definitions, or unsaved edits. No automatic refresh.
Event receivers are **POSSIBLE**, not proven; Run Process event parsing is best-effort.
The raw qualification/action `toString` representation may not contain the same
formatted text displayed in Developer Studio. Not an execution simulator.
Indices are **in memory**, erased when the view/session ends. No passwords or
workflow definitions are persisted by this plugin. Only a bounded set of the first
3000 matching occurrences is rendered; index status shows PARTIAL on fetch failures.
Use the optional name prefilter for large ITSM installations: it limits detail fetches.

UI behavior and export have been compiled against BMC 26.1.1 but require actual
Windows/SWT integration testing with a test AR server. Do not run a full index on a
production server until query load is acceptable. Indexing issues individual
getObject calls from a background Job; cancellation occurs between calls, not during
an outstanding BMC API call.

**Interaction with existing Yrell Developer Tools:** If Fast Object List changes
BMC's underlying provider results (for example a customization-type filter),
that filter may also affect the lists returned to this version of Yrell Search.
For an exhaustive inventory, disable that filtering while building the index.
The status `complete` means every object returned by the providers was processed,
not that hidden/unauthorized/filtered objects were discovered.
