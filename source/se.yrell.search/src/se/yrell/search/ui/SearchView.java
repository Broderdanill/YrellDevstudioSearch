package se.yrell.search.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.store.ARServerStore;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.store.StoreManager;
import se.yrell.search.SearchPlugin;
import se.yrell.search.bmc.IndexBuilder;
import se.yrell.search.bmc.StudioIntegration;
import se.yrell.search.core.SearchIndex;

/** Dockable Eclipse view. No AR network requests are made on the UI thread. */
public final class SearchView extends ViewPart {
    public static final String ID = "se.yrell.search.view";
    private Combo server, mode;
    private Text search, scope;
    private Table matchTable, objectTable;
    private Label status;
    private Button refresh, cancel, open, export;
    private TabFolder tabs;
    private volatile Job activeJob;
    private final AtomicLong generation = new AtomicLong();
    private SearchIndex<IModelItem> index;
    private SearchIndex.Result<IModelItem> results;
    private IStore sourceStore;
    private boolean disposed;

    @Override public void createPartControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));
        Composite first = new Composite(root, SWT.NONE);
        first.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        first.setLayout(new GridLayout(4, false));
        new Label(first, SWT.NONE).setText("AR server");
        server = new Combo(first, SWT.DROP_DOWN | SWT.READ_ONLY);
        server.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button reloadServers = new Button(first, SWT.PUSH); reloadServers.setText("Servers");
        reloadServers.addListener(SWT.Selection, e -> reloadServers());
        refresh = new Button(first, SWT.PUSH); refresh.setText("Build / Refresh index");
        refresh.addListener(SWT.Selection, e -> startIndex());

        Composite second = new Composite(root, SWT.NONE);
        second.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        second.setLayout(new GridLayout(5, false));
        new Label(second, SWT.NONE).setText("Search");
        search = new Text(second, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        search.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        search.addListener(SWT.DefaultSelection, e -> runSearch());
        mode = new Combo(second, SWT.READ_ONLY);
        for (SearchIndex.Mode m : SearchIndex.Mode.values()) mode.add(m.name());
        mode.select(0);
        mode.addListener(SWT.Selection, e -> runSearch());
        Button find = new Button(second, SWT.PUSH); find.setText("Find");
        find.addListener(SWT.Selection, e -> runSearch());
        cancel = new Button(second, SWT.PUSH); cancel.setText("Cancel index");
        cancel.setEnabled(false);
        cancel.addListener(SWT.Selection, e -> { Job job = activeJob; if (job != null) job.cancel(); });

        Composite third = new Composite(root, SWT.NONE);
        third.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        third.setLayout(new GridLayout(2, false));
        new Label(third, SWT.NONE).setText("Name prefilter (before indexing, optional)");
        scope = new Text(third, SWT.BORDER);
        scope.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        scope.setToolTipText("Fetch definitions only for objects whose name contains this text. Leave empty for full index.");

        tabs = new TabFolder(root, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        matchTable = table("Matches", new String[]{"Type", "Object", "Category", "Location", "Match", "Confidence"},
                new int[]{110, 260, 130, 165, 460, 90});
        objectTable = table("Objects (unique)", new String[]{"Type", "Object", "Matches"}, new int[]{140, 420, 90});
        for (Table t : new Table[]{matchTable, objectTable}) {
            t.addListener(SWT.DefaultSelection, e -> openSelected());
            Menu menu = new Menu(t);
            MenuItem openItem = new MenuItem(menu, SWT.PUSH); openItem.setText("Open in Developer Studio");
            openItem.addListener(SWT.Selection, e -> openSelected());
            MenuItem exportItem = new MenuItem(menu, SWT.PUSH); exportItem.setText("Export selected to DEF...");
            exportItem.addListener(SWT.Selection, e -> exportSelected());
            t.setMenu(menu);
        }
        Composite footer = new Composite(root, SWT.NONE);
        footer.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        footer.setLayout(new GridLayout(3, false));
        status = new Label(footer, SWT.NONE);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        status.setText("Select an AR server and build an index. Saved definitions only.");
        open = new Button(footer, SWT.PUSH); open.setText("Open selected");
        open.addListener(SWT.Selection, e -> openSelected());
        export = new Button(footer, SWT.PUSH); export.setText("Export selected...");
        export.addListener(SWT.Selection, e -> exportSelected());
        server.addListener(SWT.Selection, e -> {
            generation.incrementAndGet();
            Job previous = activeJob; if (previous != null) previous.cancel();
            activeJob = null; refresh.setEnabled(true); cancel.setEnabled(false);
            index = null; results = null; sourceStore = null;
            matchTable.removeAll(); objectTable.removeAll();
            status.setText("Server changed. Build an index before searching.");
        });
        reloadServers();
    }
    private Table table(String title, String[] headers, int[] widths) {
        TabItem tab = new TabItem(tabs, SWT.NONE); tab.setText(title);
        Table t = new Table(tabs, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        t.setHeaderVisible(true); t.setLinesVisible(true);
        for (int i = 0; i < headers.length; i++) {
            TableColumn c = new TableColumn(t, SWT.LEFT); c.setText(headers[i]); c.setWidth(widths[i]);
        }
        tab.setControl(t);
        return t;
    }
    private void reloadServers() {
        String previous = server.getText();
        server.removeAll();
        Collection<IStore> stores;
        try { stores = StoreManager.getInstance().getStores(); }
        catch (RuntimeException ex) { SearchPlugin.error("Could not list AR connections", ex); return; }
        for (IStore s : stores) {
            if (s instanceof ARServerStore && s.isConnected()) server.add(s.getName());
        }
        if (server.getItemCount() == 1) server.select(0);
        else for (int i = 0; i < server.getItemCount(); i++)
            if (server.getItem(i).equals(previous)) { server.select(i); break; }
    }
    private IStore selectedStore() {
        if (server.getSelectionIndex() < 0) return null;
        String name = server.getText();
        for (IStore s : StoreManager.getInstance().getStores())
            if (s instanceof ARServerStore && s.isConnected() && name.equals(s.getName())) return s;
        return null;
    }
    private void startIndex() {
        IStore chosen = selectedStore();
        if (chosen == null) { MessageDialog.openInformation(getSite().getShell(), "Yrell Search", "Select a connected AR server."); return; }
        if (activeJob != null) activeJob.cancel();
        long request = generation.incrementAndGet();
        String prefilter = scope.getText();
        refresh.setEnabled(false); cancel.setEnabled(true);
        status.setText("Indexing in Eclipse Progress... current search results remain available.");
        Display display = getSite().getShell().getDisplay();
        Job job = new Job("Yrell Search: index " + chosen.getName()) {
            @Override protected IStatus run(IProgressMonitor monitor) {
                try {
                    SearchIndex<IModelItem> next = IndexBuilder.build(chosen, prefilter, monitor);
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    display.asyncExec(() -> {
                        if (disposed || request != generation.get() || !chosen.isConnected()) return;
                        index = next; sourceStore = chosen;
                        refresh.setEnabled(true); cancel.setEnabled(false); activeJob = null;
                        status.setText(next.size() + " objects indexed; " +
                                (next.complete() ? "processed returned lists" : "PARTIAL: " + next.problems().size() + " errors") +
                                " · saved definitions · " + chosen.getName());
                        runSearch();
                    });
                    return next.complete() ? Status.OK_STATUS : new Status(IStatus.WARNING, SearchPlugin.ID,
                            "Index is PARTIAL: " + next.problems().size() + " definitions/types were not read.");
                } catch (Throwable ex) {
                    SearchPlugin.error("Workflow indexing failed", ex);
                    display.asyncExec(() -> {
                        if (disposed || request != generation.get()) return;
                        refresh.setEnabled(true); cancel.setEnabled(false); activeJob = null;
                        status.setText("Index failed. See Error Log.");
                    });
                    return new Status(IStatus.ERROR, SearchPlugin.ID, "Indexing failed", ex);
                } finally {
                    if (monitor.isCanceled()) display.asyncExec(() -> {
                        if (!disposed && request == generation.get()) {
                            refresh.setEnabled(true); cancel.setEnabled(false); activeJob = null;
                            status.setText("Indexing cancelled. Previous index retained.");
                        }
                    });
                }
            }
        };
        job.setUser(true);
        activeJob = job;
        job.schedule();
    }
    private void runSearch() {
        matchTable.removeAll(); objectTable.removeAll(); results = null;
        if (index == null || sourceStore == null || !sourceStore.isConnected()) {
            status.setText("Build an index for a connected server first."); return;
        }
        if (selectedStore() != sourceStore || !Objects.equals(sourceStore.getUser(), index.user()) ||
                (sourceStore instanceof com.bmc.arsys.studio.model.store.IARServerStore ar
                        && !String.valueOf(ar.getOverlayGroup()).equals(index.overlay()))) {
            status.setText("Connection/account/overlay changed. Refresh index."); return;
        }
        SearchIndex.Mode modeValue = SearchIndex.Mode.values()[mode.getSelectionIndex()];
        results = index.search(search.getText(), modeValue, 3000);
        matchTable.setRedraw(false); objectTable.setRedraw(false);
        try {
            for (SearchIndex.Hit<IModelItem> hit : results.hits()) {
                TableItem row = new TableItem(matchTable, SWT.NONE);
                row.setText(new String[]{hit.entry().type(), hit.entry().name(), hit.occurrence().category(),
                    hit.occurrence().location(), preview(hit.occurrence().value()), hit.occurrence().confidence().name()});
                row.setData(hit.entry().object());
            }
            for (SearchIndex.Entry<IModelItem> entry : results.objects()) {
                TableItem row = new TableItem(objectTable, SWT.NONE);
                long hits = results.hits().stream().filter(h -> h.entry() == entry).count();
                row.setText(new String[]{entry.type(), entry.name(), Long.toString(hits)});
                row.setData(entry.object());
            }
        } finally { matchTable.setRedraw(true); objectTable.setRedraw(true); }
        status.setText(results.totalHits() + " matching occurrences in " + results.objects().size() + " objects"
                + (results.truncated() ? " (display limited to 3000)" : "")
                + (!index.complete() ? " · PARTIAL INDEX" : "")
                + " · " + index.server());
    }
    private static String preview(String text) {
        String flat = text.replace('\r',' ').replace('\n',' ');
        return flat.length() > 320 ? flat.substring(0, 317) + "..." : flat;
    }
    private List<IModelItem> selection() {
        Table table = tabs.getSelectionIndex() == 1 ? objectTable : matchTable;
        List<IModelItem> selected = new ArrayList<>();
        for (TableItem row : table.getSelection()) {
            if (row.getData() instanceof IModelItem item) selected.add(item);
        }
        return selected;
    }
    private void openSelected() {
        List<IModelItem> items = selection();
        if (items.isEmpty()) return;
        if (!checkCurrent()) return;
        StudioIntegration.open(getSite().getShell(), items);
    }
    private void exportSelected() {
        List<IModelItem> items = selection();
        if (items.isEmpty()) return;
        if (!checkCurrent()) return;
        StudioIntegration.export(getSite().getShell(), items);
    }
    private boolean checkCurrent() {
        if (index == null || selectedStore() != sourceStore || !sourceStore.isConnected()
                || !Objects.equals(sourceStore.getUser(), index.user())
                || (sourceStore instanceof com.bmc.arsys.studio.model.store.IARServerStore ar
                    && !String.valueOf(ar.getOverlayGroup()).equals(index.overlay()))) {
            MessageDialog.openWarning(getSite().getShell(), "Yrell Search", "AR connection/account/overlay changed. Refresh before opening/exporting.");
            return false;
        }
        return true;
    }
    @Override public void setFocus() { if (search != null && !search.isDisposed()) search.setFocus(); }
    @Override public void dispose() {
        disposed = true; generation.incrementAndGet();
        Job job = activeJob; if (job != null) job.cancel();
        super.dispose();
    }
}
