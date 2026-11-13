# Yrell Devstudio Search 0.2.3

Standalone, read-only Eclipse/OSGi search view (`se.yrell.search`) for classic BMC Developer Studio **26.1.1 build001**, Java 17. Based on the supplied 0.2.1 sources.

## What changed in 0.2.3

**SDK test compatibility fix:** the `FormIndexBuilderOfflineTest` now uses dynamic
proxies for `IModelObject`, `IModelItem`, `IStore` and `IProgressMonitor` instead
of concrete test classes. This removes compilation failures from SDK-specific
abstract methods. The offline stub uses the real SDK's `Set` return type and
`ItemList.addItem` method. Source and plugin functionality are otherwise unchanged
from 0.2.2, including Forms, field searches and connection navigation.

### 0.2.2 functionality retained


**Forms are now first-class search objects**, alongside Filters, Active Links, Escalations, Filter Guides and Active Link Guides. Form definitions and their field definitions participate in the existing query engine and in the same result/external-open/DEF-export workflow.

- **Search in → Forms** checkbox; multiple object types can still be selected without rebuilding an index.
- **Field metadata search:** Field ID, field name, label, data type, field type, menu, help text, default and optional view names, if exposed by the provider. Search hits show the **parent form**, `Field <ID> · <name>`, the specific property and its value.
- **New query tokens** `fieldid:`, `fieldname:`, `label:`, `datatype:`, `fieldtype:`, `menu:`, `view:`. Existing `field:` also searches form fields, and `form:` matches form definitions plus workflow form references. Unrecognized `x:y` remains plain text.
- **Content modes** distinguish fields, form references, and form/field definitions; type checkboxes remain the primary object-scope filter.
- **Connections** now resolve workflow form references into an indexed Form object where the destination name matches exactly. Incoming workflow references are visible from the Form. References to absent or dynamically named forms remain unresolved.
- **Refresh changed** always rereads Forms and their fields even if the parent Form's timestamp is unchanged. Workflow definitions still use the original timestamp-safe reuse optimization.
- **Index details** show form counts and the number of extracted field IDs. If the BMC adapter cannot enumerate a form's fields, the form name is still searchable but the index is explicitly **PARTIAL**; an unavailable field catalogue is never silently treated as an empty form.

### Example queries

| Search text | Result |
|---|---|
| `form:"HPD:Help Desk"` | The Form object and workflow references to the form |
| `type:Form` | Form definitions (if Forms checkbox is selected) |
| `fieldid:536870913` | The exact Field ID in a form definition, plus exact workflow field-ID references |
| `fieldname:"Short Description"` | Forms that contain a field with a matching name |
| `field:"Status"` | Field names/labels/IDs and existing workflow field references |
| `form:"HPD:Help Desk" fieldid:536870913` | Forms satisfying BOTH clauses (object-level AND) |
| `label:"Status"` | A form's field label when the provider exposes it |
| `datatype:CHAR` | Field metadata data type (some provider versions expose numeric type codes instead) |
| `menu:"My Menu"` | Named field menus and workflow menu references |
| `fieldid:7 OR fieldname:Status` | Either term (object-level OR) |
| `type:Form -fieldname:"Temporary"` | Forms excluding matching field names |

**Exact numeric IDs:** `fieldid:7` matches `7` but not `17`. Bare `field:7` retains substring semantics for backward compatibility. A field hit belongs to its **Form**: double-click opens the native form editor and DEF export exports the containing form once, no matter how many fields matched. The plugin does not create stand-alone field editors or export individual fields.

## Install / update from Linux

The release ZIP contains **full source and automated build/update scripts, not a prebuilt 0.2.3 plugin JAR**. Because the BMC Developer Studio SDK is proprietary, the plugin must be compiled against the locally installed Developer Studio 26.1.1 libraries. This also validates the Java API against the exact target version.

Download these two files to the same directory (normally `~/Downloads`):

- `YrellDevstudioSearch-0.2.3-FullSource.zip`
- `yrell-search-0203-update.sh`

For the existing Git checkout used in the supplied 0.2.1 scripts:

```bash
bash ~/Downloads/yrell-search-0203-update.sh
```

Optional custom Git repository and SDK path:

```bash
bash ~/Downloads/yrell-search-0203-update.sh \
  /home/dice/Git/YrellDevstudioSearch \
  /home/dice/Downloads/componentDeveloperStudioWindows.zip
```

The script performs SDK preflight, extracts the release in a temporary directory, runs the offline search/form regression suite, builds with your BMC libraries, runs the full regression suite, **then** backs up the existing Git source tree (excluding `.git` and `.build`), installs 0.2.3 and copies `se.yrell.search_0.2.3.jar` to `~/Downloads`. Build/test failure leaves the Git checkout unchanged. A backup of the old sources is written to `~/Downloads/YrellDevstudioSearch-backup-<timestamp>/`.

Install `se.yrell.search_0.2.3.jar` to Developer Studio's Windows `plugins` directory, removing any older `se.yrell.search_0.2.1.jar` or `se.yrell.search_0.2.2.jar` file. Launch once with `-clean` and open **Window → Show View → Other → Yrell Search**.

### Manual build

```bash
cd /home/dice/Git/YrellDevstudioSearch
bash scripts/test-core-offline.sh
bash scripts/build.sh /home/dice/Downloads/componentDeveloperStudioWindows.zip
bash scripts/test.sh
```

## Verification checklist in Developer Studio

1. Select a connected AR server and **Build / Refresh index** with an empty *object-name prefilter*. Confirm Forms appear in **Index details** alongside workflow.
2. Select **Forms** alone and query `type:Form`. Double-click a result to confirm native editor opening.
3. Search a known field using `fieldid:<numeric ID>` and `fieldname:"<name>"`. In **Matches**, verify the matching location says `Field <ID> · <name>` and that **Objects** contains only the parent form.
4. Search `form:"<form>"` with workflow and Forms selected, and verify both sides appear. Select a workflow reference, then view **Connections**; if the form was indexed, it should resolve and open correctly.
5. Test **Export selected** for a Form and for mixed workflow/Form selections through BMC's native DEF dialog.
6. Change a field on the server, click **Refresh changed**, and ensure the refreshed field metadata is searchable; a changed field cannot be retained solely because the Form's own timestamp was unchanged.
7. If field searches return no results but form names do, open **Index details** and check for a per-form *field catalogue unavailable* message. This indicates a BMC API incompatibility or missing permissions; do not interpret it as the form having no fields.

## Design / limitations

- 100% read-only metadata search. No AR writes, Java agent, external services, REST login, or database access. Index exists in memory only. BMC's native opening and DEF export operations are reused.
- Field metadata is retrieved via a small, **public-API read-only method adapter**: preferentially the AR Java API `getListFieldObjects(formName)` on the studio-exposed server session, or the field catalogue exposed by a BMC Form model. This avoids hardwiring internal implementation classes that differ between SDK builds. It also means actual BMC field enumeration **needs integration verification**. Failure is marked partial; the plugin will never invent field definitions.
- BMC field labels may be view-specific; only labels/views exposed by the public model are indexed. No claim is made that all VUI labels are available. Data type may be an integer constant in some BMC API versions.
- Only classic AR System Form and workflow definitions, not Innovation Studio record definitions, data records, or unsaved editor changes.
- Definitions returned by the current server connection/account/overlay only; permission limits or another plugin prefilter can cause incomplete coverage. Dynamic workflow destinations remain conditional rather than proven runtime links.
- Heavy indexing and search remain Eclipse background jobs. Results still show at most 2,000 **occurrences** in the UI; the underlying unique matching object set is not truncated for export.
- The default object-name prefilter only filters **Form/workflow object names**, not field names. For comprehensive field searches leave it empty.
- Offline tests validate the query engine, form-metadata reflection path and reference graph. The actual Windows SWT screen, BMC integration, form editor and DEF export must be tested against your installed Developer Studio. This environment does not contain BMC's proprietary SDK binaries and therefore cannot produce a correctly SDK-validated JAR on its own.

## Structure

- `source/se.yrell.search/` — complete plugin code and OSGi metadata.
- `tests/` — SDK-backed regression tests and separate offline-only BMC interface stubs.
- `scripts/build.sh`, `scripts/test.sh` — compile/test with your BMC Developer Studio SDK.
- `scripts/test-core-offline.sh` — independent search and form-metadata tests; test stubs never ship in the plugin JAR.
- `scripts/install-to-git.sh`, `scripts/update-external.sh` — source merge and full safe upgrade.
