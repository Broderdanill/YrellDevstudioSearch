Yrell Devstudio Search 0.2.1
===========================

BMC Developer Studio 26.1.1 Java 17 plugin; standalone OSGi ID se.yrell.search.
The source tree, build/test scripts, comprehensive feature notes and limitations
are in the parent project README.md. No external libraries are shipped in JAR.

Open Window > Show View > Other > Yrell Search > Yrell Search.
Indexing uses an Eclipse Job. Check one or more workflow types. The virtual result
lists include exact matches, unique objects for export and conservative connections.
The plugin never writes to AR Server; uses native BMC editor and DEF export.

0.2.1 adds multi-copy names in result context menus, exact-name opening of workflow
on another connected AR server, and a compact visual map above Connections.
Cross-server opening does not export, copy, compare or deploy definitions.
