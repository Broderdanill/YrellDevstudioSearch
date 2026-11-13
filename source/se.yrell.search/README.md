Yrell Devstudio Search 0.2.3
===========================

Java 17 Eclipse/OSGi plugin for BMC Developer Studio 26.1.1 build001.
Bundle ID: se.yrell.search.

Searches saved AR System Forms and their field definitions as well as Filters,
Active Links, Escalations, Filter Guides and Active Link Guides. The Forms checkbox
includes form fields; Matches shows the field ID/name/property, while Open and
DEF export operate on the parent Form. Field-level search includes fieldid:,
fieldname:, label:, datatype:, fieldtype:, menu: and view:.

The Connections map resolves indexed Forms referenced by workflow and still
shows unresolved references where they cannot be statically identified.
The plugin is read-only for server definitions and index data stays in memory.
BMC's native editor and DEF export UI are reused.

See project README.md for build/update instructions, examples and limitations.
This source release does not distribute a JAR compiled against BMC SDK binaries.
