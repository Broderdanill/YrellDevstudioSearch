package se.yrell.search.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.store.ARServerStore;
import com.bmc.arsys.studio.model.store.IARServerStore;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.store.StoreManager;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import se.yrell.search.SearchPlugin;
import se.yrell.search.bmc.IndexBuilder;
import se.yrell.search.bmc.StudioIntegration;
import se.yrell.search.bmc.CrossServerOpener;
import se.yrell.search.core.ObjectNames;
import se.yrell.search.core.ReferenceGraph;
import se.yrell.search.core.SearchIndex;

/** BMC dockable view. All AR reads happen in a Job; SWT lists are virtual for large search results. */
public final class SearchView extends ViewPart {
    public static final String ID = "se.yrell.search.view";
    private static final int MAX_SHOWN_MATCHES = 2_000;
    // Serialize all BMC API-heavy index jobs within our plugin; cancel alone cannot interrupt a blocked AR call.
    private static final ISchedulingRule INDEX_RULE = new ISchedulingRule() {
        @Override public boolean contains(ISchedulingRule rule) { return rule == this; }
        @Override public boolean isConflicting(ISchedulingRule rule) { return rule == this; }
    };
    private static final String[] TYPES = { IARSystemTypes.FILTER.getTypeName(), IARSystemTypes.ACTIVE_LINK.getTypeName(),
            IARSystemTypes.ESCALATION.getTypeName(), IARSystemTypes.FILTER_GUIDE.getTypeName(),
            IARSystemTypes.ACTIVE_LINK_GUIDE.getTypeName(), IARSystemTypes.FORM.getTypeName() };
    private static final String[] LABELS = { "Filters", "Active Links", "Escalations", "Filter Guides", "Active Link Guides", "Forms" };
    private Combo server, mode;
    private Text query, namePrefilter, details;
    private Table matchTable, objectTable, relationTable;
    private ConnectionOverview connectionOverview;
    private Label status;
    private Button refresh, refreshChanged, cancel, advancedToggle;
    private Composite advanced;
    private TabFolder tabs;
    private final Button[] typeChecks = new Button[TYPES.length];
    private final List<IStore> serverStores = new ArrayList<>();
    private volatile Job activeJob;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong searchGeneration = new AtomicLong();
    private SearchIndex<IModelItem> index;
    private SearchIndex.Result<IModelItem> results;
    private ReferenceGraph<IModelItem> graph;
    private final List<ReferenceGraph.Link<IModelItem>> currentLinks = new ArrayList<>();
    private SearchIndex.Entry<IModelItem> focusedEntry;
    private IStore sourceStore;
    private String indexedNamePrefilter = "";
    private boolean disposed;

    @Override public void createPartControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.marginWidth = 8; rootLayout.marginHeight = 7; rootLayout.verticalSpacing = 7;
        root.setLayout(rootLayout);

        Composite serverBar = line(root, 5);
        new Label(serverBar, SWT.NONE).setText("AR server");
        server = new Combo(serverBar, SWT.DROP_DOWN | SWT.READ_ONLY);
        server.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button servers = button(serverBar, "Reload servers", "Refresh connected AR servers");
        servers.addListener(SWT.Selection, e -> reloadServers());
        refresh = button(serverBar, "Build / Refresh index", "Fetch saved workflow, forms and field metadata in Eclipse Progress");
        refresh.addListener(SWT.Selection, e -> startIndex(false));
        refreshChanged = button(serverBar, "Refresh changed", "Reuse definitions whose BMC modification timestamps are unchanged; rescan all names to detect deletions");
        refreshChanged.setEnabled(false);
        refreshChanged.addListener(SWT.Selection, e -> startIndex(true));

        Composite searchLine = line(root, 3);
        query = new Text(searchLine, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        query.setMessage("Search workflows, forms, field IDs, field names...");
        query.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        query.addListener(SWT.DefaultSelection, e -> runSearch());
        mode = new Combo(searchLine, SWT.READ_ONLY);
        mode.setItems(new String[]{"Everywhere", "Events", "Actions", "Fields (definitions & references)", "Forms (names & references)", "References", "Form/field metadata"});
        mode.select(0);
        mode.setToolTipText("Optional content filter; use Everywhere for broad searches");
        mode.addListener(SWT.Selection, e -> runSearch());
        Button find = button(searchLine, "Search", "Search the local index (no AR server request)");
        find.addListener(SWT.Selection, e -> runSearch());

        Composite typeBar = line(root, 9);
        new Label(typeBar, SWT.NONE).setText("Search in:");
        for (int i = 0; i < TYPES.length; i++) {
            final int pos = i;
            Button cb = new Button(typeBar, SWT.CHECK);
            cb.setText(LABELS[i]); cb.setSelection(true);
            cb.setToolTipText("Include " + LABELS[i] + " in the current search; no reindex required");
            cb.addListener(SWT.Selection, e -> runSearch());
            typeChecks[pos] = cb;
        }
        Button all = button(typeBar, "All", "Search all object types");
        all.addListener(SWT.Selection, e -> { for (Button b : typeChecks) b.setSelection(true); runSearch(); });
        Button none = button(typeBar, "None", "Clear all object type selections");
        none.addListener(SWT.Selection, e -> { for (Button b : typeChecks) b.setSelection(false); runSearch(); });

        Composite tools = line(root, 4);
        advancedToggle = new Button(tools, SWT.CHECK);
        advancedToggle.setText("Index options / Search help");
        advancedToggle.setSelection(false);
        Button indexDetails = button(tools, "Index details...", "See indexed object types, completeness and read errors");
        indexDetails.addListener(SWT.Selection, e -> showIndexDetails());
        cancel = button(tools, "Cancel indexing", "Stop the current index build; previous successful index is retained");
        cancel.setEnabled(false);
        Label hint = new Label(tools, SWT.NONE);
        hint.setText("Enter to search · double-click to open · multi-select to export");
        hint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        advanced = line(root, 2);
        GridData advancedData = (GridData) advanced.getLayoutData();
        advancedData.exclude = true; advanced.setVisible(false);
        new Label(advanced, SWT.NONE).setText("Object-name prefilter for NEXT index build (NOT a field-name filter)");
        namePrefilter = new Text(advanced, SWT.BORDER);
        namePrefilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        namePrefilter.setToolTipText("Only form/workflow object names are tested; field names are NOT tested. Leave empty to index all forms and fields.");
        Label help = new Label(advanced, SWT.WRAP);
        help.setText("Queries: event:ChildClosed · form:\"HPD:Help Desk\" · action:PushFields · field:536870913 · " +
                "fieldid:536870913 · fieldname:\"Short Description\" · label:Status · datatype:CHAR · " +
                "name:Incident · branch:Else · term1 term2 (AND) · term1 OR term2 · -excluded");
        GridData helpData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        helpData.widthHint = 750; help.setLayoutData(helpData);
        advancedToggle.addListener(SWT.Selection, e -> {
            boolean show = advancedToggle.getSelection();
            advanced.setVisible(show); advancedData.exclude = !show;
            root.layout(true, true);
        });
        cancel.addListener(SWT.Selection, e -> { Job job = activeJob; if (job != null) job.cancel(); });

        SashForm sash = new SashForm(root, SWT.VERTICAL | SWT.SMOOTH);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        tabs = new TabFolder(sash, SWT.NONE);
        matchTable = newTable("Matches", new String[]{"Type", "Object", "Match type", "Where", "Text / value", "Confidence"},
                new int[]{105, 260, 135, 230, 420, 88}, true);
        objectTable = newTable("Objects", new String[]{"Type", "Object", "Matches"},
                new int[]{140, 420, 100}, true);
        relationTable = newConnectionTab();
        matchTable.addListener(SWT.Selection, e -> focusedFromSelection(matchTable));
        objectTable.addListener(SWT.Selection, e -> focusedFromSelection(objectTable));
        matchTable.addListener(SWT.DefaultSelection, e -> openSelected());
        objectTable.addListener(SWT.DefaultSelection, e -> openSelected());
        relationTable.addListener(SWT.DefaultSelection, e -> openSelected());
        relationTable.addListener(SWT.Selection, e -> relationDetails());
        for (Table t : new Table[]{matchTable, objectTable, relationTable}) installContextMenu(t);
        details = new Text(sash, SWT.READ_ONLY | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
        details.setText("Select a match to see the exact source location and its value. Connections appear in a separate tab.");
        sash.setWeights(new int[]{76,24});

        Composite footer = line(root, 6);
        status = new Label(footer, SWT.NONE);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        status.setText("Select an AR server and build an index. Saved definitions only (including forms and fields).");
        Button copyNames = button(footer, "Copy names", "Copy names of selected objects, one per line");
        copyNames.addListener(SWT.Selection, e -> copySelectedNames());
        Button open = button(footer, "Open selected", "Open highlighted objects using BMC's editor");
        open.addListener(SWT.Selection, e -> openSelected());
        Button export = button(footer, "Export selected...", "BMC DEF export for highlighted objects");
        export.addListener(SWT.Selection, e -> exportSelected());
        Button exportAll = button(footer, "Export all matches...", "Export every unique matching workflow/form; a field match exports its parent form");
        exportAll.addListener(SWT.Selection, e -> exportAll());
        Button reset = button(footer, "Clear", "Clear query and reset scope");
        reset.addListener(SWT.Selection, e -> { query.setText(""); for (Button cb : typeChecks) cb.setSelection(true); mode.select(0); clearResults(); });
        server.addListener(SWT.Selection, e -> invalidateIndex("Server changed. Build an index before searching."));
        tabs.addListener(SWT.Selection, e -> { if (tabs.getSelectionIndex() == 2) refreshRelations(); });
        reloadServers();
    }
    private static Composite line(Composite parent, int columns) {
        Composite c = new Composite(parent, SWT.NONE);
        c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout l = new GridLayout(columns, false);
        l.marginWidth = 0; l.marginHeight = 0;
        c.setLayout(l);
        return c;
    }
    private static Button button(Composite parent, String text, String tooltip) {
        Button b = new Button(parent, SWT.PUSH); b.setText(text); b.setToolTipText(tooltip); return b;
    }
    private Table newTable(String name, String[] columns, int[] widths, boolean virtual) {
        TabItem tab = new TabItem(tabs, SWT.NONE); tab.setText(name);
        Table t = new Table(tabs, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL |
                (virtual ? SWT.VIRTUAL : SWT.NONE));
        t.setHeaderVisible(true); t.setLinesVisible(true);
        for (int i=0;i<columns.length;i++) {
            TableColumn c = new TableColumn(t,SWT.LEFT); c.setText(columns[i]); c.setWidth(widths[i]);
        }
        if (virtual) t.addListener(SWT.SetData, e -> {
            if (results == null || !(e.item instanceof TableItem item)) return;
            int row = t.indexOf(item);
            if (row < 0) return;
            if (t == matchTable && row < results.hits().size()) {
                SearchIndex.Hit<IModelItem> hit = results.hits().get(row);
                item.setText(new String[]{hit.entry().type(), hit.entry().name(), hit.occurrence().category(),
                        hit.occurrence().location(), preview(hit.occurrence().value()), hit.occurrence().confidence().name()});
            } else if (t == objectTable && row < results.objects().size()) {
                SearchIndex.Entry<IModelItem> entry = results.objects().get(row);
                item.setText(new String[]{entry.type(), entry.name(), String.valueOf(results.hitsFor(entry))});
            }
        });
        tab.setControl(t); return t;
    }
    private Table newConnectionTab() {
        TabItem tab = new TabItem(tabs, SWT.NONE);
        tab.setText("Connections");
        Composite pane = new Composite(tabs, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0; layout.marginHeight = 0; layout.verticalSpacing = 5;
        pane.setLayout(layout);
        connectionOverview = new ConnectionOverview(pane);
        GridData overviewData = new GridData(SWT.FILL, SWT.TOP, true, false);
        overviewData.heightHint = 145;
        connectionOverview.setLayoutData(overviewData);
        Table t = new Table(pane, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        t.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        t.setHeaderVisible(true); t.setLinesVisible(true);
        String[] names={"Direction", "Connection", "Target / source", "Where", "Confidence"};
        int[] widths={100,190,310,220,100};
        for (int i=0;i<names.length;i++) {
            TableColumn col = new TableColumn(t, SWT.LEFT);
            col.setText(names[i]); col.setWidth(widths[i]);
        }
        tab.setControl(pane);
        return t;
    }

    /** Native SWT context menu, rebuilt as connections change; selection is resolved at click time. */
    private void installContextMenu(Table table) {
        Menu menu = new Menu(table);
        MenuItem copy = new MenuItem(menu, SWT.PUSH);
        copy.setText("Copy Object Name(s)");
        copy.addListener(SWT.Selection, e -> copySelectedNames());
        new MenuItem(menu, SWT.SEPARATOR);
        MenuItem open = new MenuItem(menu, SWT.PUSH);
        open.setText("Open in Developer Studio");
        open.addListener(SWT.Selection, e -> openSelected());
        MenuItem alternate = new MenuItem(menu, SWT.CASCADE);
        alternate.setText("Open on Server");
        Menu servers = new Menu(menu);
        alternate.setMenu(servers);
        new MenuItem(menu, SWT.SEPARATOR);
        MenuItem export = new MenuItem(menu, SWT.PUSH);
        export.setText("Export selected to DEF...");
        export.addListener(SWT.Selection, e -> exportSelected());
        menu.addListener(SWT.Show, e -> {
            boolean hasItems = !selection().isEmpty();
            copy.setEnabled(hasItems);
            open.setEnabled(hasItems);
            export.setEnabled(hasItems);
            for (MenuItem m: servers.getItems()) m.dispose();
            int available = 0;
            if (hasItems && current()) {
                for (IStore other: connectedAlternateStores()) {
                    MenuItem option = new MenuItem(servers, SWT.PUSH);
                    option.setText(other.getName() + " · " + other.getUser() +
                            (other instanceof IARServerStore ar ? " · overlay " + ar.getOverlayGroup() : ""));
                    option.addListener(SWT.Selection, event -> openOnOtherServer(other));
                    available++;
                }
            }
            if (available == 0) {
                MenuItem empty = new MenuItem(servers, SWT.PUSH);
                empty.setEnabled(false);
                empty.setText("No other connected AR server");
            }
            alternate.setEnabled(hasItems && available>0);
        });
        table.addListener(SWT.MenuDetect, e -> {
            if (e.x<0 || e.y<0) return;
            Point location=table.toControl(new Point(e.x,e.y));
            TableItem clicked=table.getItem(location);
            if(clicked!=null && !table.isSelected(table.indexOf(clicked))) {
                table.setSelection(clicked);
                if(table==matchTable || table==objectTable) focusedFromSelection(table);
            }
        });
        table.setMenu(menu);
    }

    private List<IStore> connectedAlternateStores() {
        List<IStore> others=new ArrayList<>();
        try {
            for (IStore store: StoreManager.getInstance().getStores()) {
                if(store instanceof ARServerStore && store!=sourceStore && store.isConnected()) others.add(store);
            }
        } catch (RuntimeException ex) { SearchPlugin.error("Cannot enumerate target AR servers",ex); }
        return others;
    }

    private void copySelectedNames() {
        List<IModelItem> selected=selection();
        if(selected.isEmpty()) return;
        String text=ObjectNames.lines(selected.stream().map(IModelItem::getName).toList());
        if (text.isEmpty()) return;
        Clipboard clipboard=new Clipboard(getSite().getShell().getDisplay());
        try { clipboard.setContents(new Object[]{text},new Transfer[]{TextTransfer.getInstance()}); }
        finally { clipboard.dispose(); }
        status.setText("Copied " + ObjectNames.uniqueCount(selected.stream().map(IModelItem::getName).toList()) +
                " object name(s) to clipboard.");
    }

    private void openOnOtherServer(IStore target) {
        if(!canOperate() || target==sourceStore || !target.isConnected()) return;
        List<CrossServerOpener.ObjectKey> keys=CrossServerOpener.keys(selection());
        if (keys.isEmpty()) return;
        long request=generation.get();
        Display display=getSite().getShell().getDisplay();
        status.setText("Looking up " + keys.size() + " object(s) on " + target.getName() + " in Progress...");
        Job job = new Job("Yrell Search: Open on " + target.getName()) {
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    CrossServerOpener.Resolution found=CrossServerOpener.resolve(target,keys,monitor);
                    if(monitor.isCanceled()) return Status.CANCEL_STATUS;
                    display.asyncExec(() -> {
                        if(disposed || request!=generation.get() || !target.isConnected()) return;
                        if(!found.missing().isEmpty() || !found.errors().isEmpty()) {
                            StringBuilder reason=new StringBuilder();
                            reason.append("On ").append(target.getName()).append(": ")
                                  .append(found.resolved().size()).append(" found, ")
                                  .append(found.missing().size()).append(" missing, ")
                                  .append(found.errors().size()).append(" retrieval errors.\n\n");
                            int max=12;
                            for(String missing:found.missing()) { if(max--<=0) break; reason.append("Not found: ").append(missing).append("\n"); }
                            for(String error:found.errors()) { if(max--<=0) break; reason.append("Error: ").append(error).append("\n"); }
                            MessageDialog.openWarning(getSite().getShell(),"Yrell Search – Cross-server open",reason.toString());
                        }
                        if(!found.resolved().isEmpty()) StudioIntegration.open(getSite().getShell(),found.resolved());
                        status.setText("Open on " + target.getName() + ": " + found.resolved().size() +
                                " found · " + found.missing().size() + " missing · " + found.errors().size() + " errors");
                    });
                    return found.errors().isEmpty()?Status.OK_STATUS:
                            new Status(IStatus.WARNING,SearchPlugin.ID,"Some target definitions could not be resolved");
                } catch (Exception ex) {
                    SearchPlugin.error("Cross-server open failed",ex);
                    return new Status(IStatus.ERROR,SearchPlugin.ID,"Cross-server open failed",ex);
                }
            }
        };
        job.setRule(INDEX_RULE); job.setUser(true); job.schedule();
    }

    private void reloadServers() {
        IStore before = selectedStore();
        server.removeAll(); serverStores.clear();
        Collection<IStore> stores;
        try { stores = StoreManager.getInstance().getStores(); }
        catch (RuntimeException ex) { SearchPlugin.error("Cannot enumerate AR connections", ex); return; }
        for (IStore s : stores) {
            if (s instanceof ARServerStore && s.isConnected()) { serverStores.add(s); server.add(s.getName()); }
        }
        int selected = serverStores.indexOf(before);
        if (selected >= 0) server.select(selected);
        else if (serverStores.size() == 1) server.select(0);
        if (before != selectedStore()) invalidateIndex("Server connection changed. Rebuild index.");
    }
    private IStore selectedStore() {
        int i = server == null ? -1 : server.getSelectionIndex();
        return i < 0 || i >= serverStores.size() ? null : serverStores.get(i);
    }
    private void invalidateIndex(String message) {
        generation.incrementAndGet(); searchGeneration.incrementAndGet();
        Job job = activeJob; if (job != null) job.cancel();
        activeJob = null; refresh.setEnabled(true); refreshChanged.setEnabled(false); cancel.setEnabled(false);
        index = null; results = null; graph = null; sourceStore = null; focusedEntry = null; indexedNamePrefilter = "";
        clearResults(); status.setText(message);
    }
    private void clearResults() {
        results = null; focusedEntry = null; currentLinks.clear();
        matchTable.setItemCount(0); objectTable.setItemCount(0); relationTable.removeAll();
        if(connectionOverview!=null && !connectionOverview.isDisposed())
            connectionOverview.showObject(null,List.of(),List.of());
        details.setText("Search saved workflows, forms and fields. Field matches open/export their parent form.");
    }
    private void startIndex(boolean incremental) {
        IStore chosen = selectedStore();
        if (chosen == null) { MessageDialog.openInformation(getSite().getShell(),"Yrell Search","Select a connected AR server first."); return; }
        if (activeJob != null) activeJob.cancel();
        long request = generation.incrementAndGet();
        // A complete prior index is required for a correct incremental refresh.
        if (incremental && (!current() || !indexedNamePrefilter.isEmpty() || !namePrefilter.getText().isBlank())) {
            MessageDialog.openInformation(getSite().getShell(),"Yrell Search",
                    "Build a complete index first; incremental refresh cannot reuse a partial/prefiltered index."); return;
        }
        SearchIndex<IModelItem> prior = incremental ? index : null;
        String prefilter = namePrefilter.getText();
        refresh.setEnabled(false); refreshChanged.setEnabled(false); cancel.setEnabled(true);
        status.setText("Indexing in Eclipse Progress. Previous index stays available until new index succeeds.");
        Display display = getSite().getShell().getDisplay();
        Job job = new Job("Yrell Search: Index definitions – " + chosen.getName()) {
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    IndexBuilder.BuildResult built = IndexBuilder.build(chosen,prefilter,prior,monitor);
                    SearchIndex<IModelItem> next = built.index();
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    ReferenceGraph<IModelItem> nextGraph = new ReferenceGraph<>(next);
                    display.asyncExec(() -> {
                        if (disposed || request != generation.get() || !chosen.isConnected()) return;
                        index = next; graph = nextGraph; sourceStore = chosen; indexedNamePrefilter = prefilter;
                        activeJob = null; refresh.setEnabled(true); refreshChanged.setEnabled(false); cancel.setEnabled(false);
                        runSearch();
                        if (query.getText().isBlank()) status.setText(next.size() + " indexed objects · " +
                                nextGraph.totalLinks() + " connections · " +
                                (next.complete() ? "ready" : "PARTIAL ("+next.problems().size()+" read problems)") +
                                (!prefilter.isBlank() ? " · NAME-PREFILTERED" : "") + " · " + next.server());
                    });
                    return next.complete() ? Status.OK_STATUS : new Status(IStatus.WARNING, SearchPlugin.ID,
                            "Partial index; " + next.problems().size() + " errors, missing types or definitions");
                } catch (Throwable ex) {
                    SearchPlugin.error("Workflow index failed", ex);
                    display.asyncExec(() -> {
                        if (disposed || request != generation.get()) return;
                        activeJob=null; refresh.setEnabled(true); refreshChanged.setEnabled(index!=null && index.complete() && indexedNamePrefilter.isBlank()); cancel.setEnabled(false);
                        status.setText("Index failed; previous index retained. See Eclipse Error Log.");
                    });
                    return new Status(IStatus.ERROR,SearchPlugin.ID,"Index failed",ex);
                } finally {
                    if (monitor.isCanceled()) display.asyncExec(() -> {
                        if (disposed || request != generation.get()) return;
                        activeJob=null; refresh.setEnabled(true); refreshChanged.setEnabled(index!=null && index.complete() && indexedNamePrefilter.isBlank()); cancel.setEnabled(false);
                        status.setText("Index cancelled; previous index retained.");
                    });
                }
            }
        };
        job.setRule(INDEX_RULE); job.setUser(true); activeJob=job; job.schedule();
    }
    private void showIndexDetails() {
        if (index == null) {
            MessageDialog.openInformation(getSite().getShell(),"Yrell Search – Index details","No index has been built yet.");
            return;
        }
        StringBuilder text=new StringBuilder();
        text.append("Server: ").append(index.server()).append("\nObjects: ").append(index.size())
            .append("\nState: ").append(index.complete()?"Complete (from returned lists)":"Partial / filtered")
            .append("\nSaved definitions only; no live editor changes.\n\n");
        for(int i=0;i<TYPES.length;i++) {
            String t=TYPES[i];
            long count=index.entries().stream().filter(e->e.type().equals(t)).count();
            text.append(LABELS[i]).append(": ").append(count).append("\n");
        }
        long fieldCount=index.entries().stream().filter(e->e.type().equals(IARSystemTypes.FORM.getTypeName()))
                .flatMap(e->e.occurrences().stream()).filter(o->o.category().equals("Field ID")).count();
        text.append("Indexed field definitions: ").append(fieldCount).append("\n");
        if(!indexedNamePrefilter.isBlank()) text.append("\nName prefilter: ").append(indexedNamePrefilter).append("\n");
        if(!index.problems().isEmpty()) {
            text.append("\nRead/index limitations:\n");
            for(String problem:index.problems()) text.append(" - ").append(problem).append("\n");
        }
        text.append("\nNote: if another plugin filters BMC's list provider, rebuild with that filter disabled for full coverage.");
        MessageDialog.openInformation(getSite().getShell(),"Yrell Search – Index details",text.toString());
    }
    private boolean current() {
        IStore s = sourceStore;
        return index != null && s != null && s == selectedStore() && s.isConnected() &&
                Objects.equals(s.getUser(),index.user()) &&
                (!(s instanceof IARServerStore ar) || Objects.equals(String.valueOf(ar.getOverlayGroup()),index.overlay()));
    }
    private void runSearch() {
        long request=searchGeneration.incrementAndGet();
        if (!current()) { clearResults(); status.setText("Build an index for the connected account/overlay first."); return; }
        Set<String> allowed=new LinkedHashSet<>();
        for(int i=0;i<typeChecks.length;i++) if(typeChecks[i].getSelection()) allowed.add(TYPES[i]);
        if(allowed.isEmpty()) { clearResults(); status.setText("Select at least one object type under Search in."); return; }
        String phrase=query.getText();
        if(phrase.isBlank()) {
            clearResults(); status.setText(index.size()+" indexed objects (workflow + forms) · enter a query to search."+
                    (!index.complete()?" · PARTIAL INDEX (see Index details)":"")); return;
        }
        SearchIndex.Mode selectedMode=SearchIndex.Mode.values()[Math.max(0,mode.getSelectionIndex())];
        SearchIndex<IModelItem> snapshot=index;
        Display display=getSite().getShell().getDisplay();
        status.setText("Searching local index...");
        Job job=new Job("Yrell Search: find matches") {
            @Override protected IStatus run(IProgressMonitor monitor) {
                SearchIndex.Result<IModelItem> found;
                try { found=snapshot.search(phrase,selectedMode,allowed,MAX_SHOWN_MATCHES); }
                catch(RuntimeException ex) { SearchPlugin.error("Search failed",ex); return new Status(IStatus.ERROR,SearchPlugin.ID,"Search failed",ex); }
                display.asyncExec(()->{
                    if(disposed || request!=searchGeneration.get() || snapshot!=index || !current()) return;
                    displayResults(found);
                });
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true); job.schedule();
    }
    private void displayResults(SearchIndex.Result<IModelItem> found) {
        clearResults(); results=found;
        if(found.queryError()!=null) { status.setText("Query: "+found.queryError()); return; }
        matchTable.setItemCount(found.hits().size()); objectTable.setItemCount(found.objects().size());
        String suffix=found.truncated()?" · displaying first "+found.hits().size()+" matches":"";
        status.setText(found.totalHits()+" occurrences · "+found.objects().size()+" unique objects"+suffix+
                (!index.complete()?" · PARTIAL INDEX":"")+" · "+index.server());
    }
    private void focusedFromSelection(Table t) {
        if (results == null) return;
        int i=t.getSelectionIndex(); if (i<0) return;
        if (t==matchTable && i<results.hits().size()) {
            SearchIndex.Hit<IModelItem> hit = results.hits().get(i);
            focusedEntry=hit.entry();
            details.setText(""+hit.entry().type()+"  ·  "+hit.entry().name()+"\n"+
                    "MATCH: "+hit.occurrence().category()+"  ("+hit.occurrence().confidence()+")\n"+
                    "LOCATION: "+hit.occurrence().location()+"\n\n"+hit.occurrence().value());
        } else if (t==objectTable && i<results.objects().size()) {
            focusedEntry=results.objects().get(i);
            details.setText(focusedEntry.type()+"  ·  "+focusedEntry.name()+"\n"+
                    results.hitsFor(focusedEntry)+" occurrences. Select Matches to inspect exact positions, or Connections for references.");
        }
        refreshRelations();
    }
    private void refreshRelations() {
        relationTable.removeAll(); currentLinks.clear();
        if (graph==null || focusedEntry==null) {
            connectionOverview.showObject(null,List.of(),List.of());
            return;
        }
        currentLinks.addAll(graph.outgoing(focusedEntry));
        currentLinks.addAll(graph.incoming(focusedEntry));
        List<String> inbound=new ArrayList<>(),outbound=new ArrayList<>();
        for(ReferenceGraph.Link<IModelItem> l:graph.incoming(focusedEntry)) inbound.add(l.from().name());
        for(ReferenceGraph.Link<IModelItem> l:graph.outgoing(focusedEntry)) {
            outbound.add(l.to()==null?l.targetName():l.to().name());
        }
        connectionOverview.showObject(focusedEntry.name(),inbound,outbound);
        for (ReferenceGraph.Link<IModelItem> l:currentLinks) {
            boolean out = l.from()==focusedEntry;
            String target = out ? l.to()==null ? l.targetName() : l.to().name() : l.from().name();
            TableItem row=new TableItem(relationTable,SWT.NONE);
            row.setText(new String[]{out?"Outgoing":"Incoming",l.relation(),target,l.location(),l.confidence().name()});
        }
        if (tabs.getSelectionIndex()==2 && currentLinks.isEmpty())
            details.setText(focusedEntry.name()+"\nNo static indexed connections. Dynamic references may not be resolvable without executing workflow.");
    }
    private void relationDetails() {
        int i=relationTable.getSelectionIndex();
        if (i<0 || i>=currentLinks.size()) return;
        ReferenceGraph.Link<IModelItem> l=currentLinks.get(i);
        details.setText(l.relation()+"  ("+l.confidence()+")\n"+
                "FROM: "+l.from().type()+" · "+l.from().name()+"\n"+
                "TO: "+(l.to()==null?l.targetName():l.to().type()+" · "+l.to().name())+"\n"+
                "WHERE: "+l.location()+"\n\nA connection does not prove that workflow executes at runtime.");
    }
    private List<IModelItem> selection() {
        int tab=tabs.getSelectionIndex();
        Table t=tab==1?objectTable:tab==2?relationTable:matchTable;
        LinkedHashSet<IModelItem> selected = new LinkedHashSet<>();
        if (results==null) return List.of();
        for (int i:t.getSelectionIndices()) {
            if (tab==0 && i<results.hits().size()) selected.add(results.hits().get(i).entry().object());
            else if (tab==1 && i<results.objects().size()) selected.add(results.objects().get(i).object());
            else if (tab==2 && i<currentLinks.size()) {
                ReferenceGraph.Link<IModelItem> l=currentLinks.get(i);
                SearchIndex.Entry<IModelItem> related=l.from()==focusedEntry?l.to():l.from();
                if (related!=null) selected.add(related.object());
            }
        }
        return List.copyOf(selected);
    }
    private boolean canOperate() {
        if (current()) return true;
        MessageDialog.openWarning(getSite().getShell(),"Yrell Search","Server/account/overlay changed. Rebuild the index before opening or exporting.");
        return false;
    }
    private void openSelected() { List<IModelItem> chosen=selection(); if (!chosen.isEmpty() && canOperate()) StudioIntegration.open(getSite().getShell(),chosen); }
    private void exportSelected() { List<IModelItem> chosen=selection(); if (!chosen.isEmpty() && canOperate()) StudioIntegration.export(getSite().getShell(),chosen); }
    private void exportAll() {
        if (results==null || results.objects().isEmpty() || !canOperate()) return;
        if (results.objects().size()>300 && !MessageDialog.openQuestion(getSite().getShell(),"Yrell Search",
                "Export "+results.objects().size()+" unique matching definitions? This may take time.")) return;
        StudioIntegration.export(getSite().getShell(),results.objects().stream().map(SearchIndex.Entry::object).toList());
    }
    private static String preview(String s) {
        String flat=s.replace('\n',' ').replace('\r',' ');
        return flat.length()>280?flat.substring(0,277)+"...":flat;
    }
    @Override public void setFocus() { if (query!=null && !query.isDisposed()) query.setFocus(); }
    @Override public void dispose() { disposed=true; generation.incrementAndGet(); searchGeneration.incrementAndGet(); Job job=activeJob; if (job!=null) job.cancel(); super.dispose(); }
}
