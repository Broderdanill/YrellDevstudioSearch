package se.yrell.search.bmc;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
import com.bmc.arsys.studio.model.store.IARServerStore;
import com.bmc.arsys.studio.model.store.IModelObject;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import com.bmc.arsys.studio.model.type.IModelType;
import se.yrell.search.core.SearchIndex;

/** Fetches saved AR definitions on an Eclipse worker; never called on SWT UI thread. */
public final class IndexBuilder {
    private static final List<IModelType> TYPES = List.of(IARSystemTypes.ACTIVE_LINK,
            IARSystemTypes.FILTER, IARSystemTypes.ESCALATION,
            IARSystemTypes.ACTIVE_LINK_GUIDE, IARSystemTypes.FILTER_GUIDE);
    private IndexBuilder() {}

    public static SearchIndex<IModelItem> build(IStore store, String nameFilter, IProgressMonitor monitor) {
        List<SearchIndex.Entry<IModelItem>> entries = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String prefix = nameFilter == null ? "" : nameFilter.strip().toLowerCase(java.util.Locale.ROOT);
        String overlay = store instanceof IARServerStore ar ? String.valueOf(ar.getOverlayGroup()) : "";
        monitor.beginTask("Indexing saved AR workflow definitions", IProgressMonitor.UNKNOWN);
        try {
            for (IModelType type : TYPES) {
                if (monitor.isCanceled()) break;
                if (!store.isConnected()) { failures.add("Connection lost; index incomplete"); break; }
                if (!store.getSupportedTypes().contains(type)) continue;
                monitor.subTask("Listing " + type.getTypeName());
                ItemList<IModelItem> list;
                try {
                    list = store.getList(type, false);
                } catch (Exception ex) {
                    failures.add(type.getTypeName() + ": list failed (" + ex.getClass().getSimpleName() + ")");
                    continue;
                }
                if (list == null) { failures.add(type.getTypeName() + ": returned no object list"); continue; }
                for (IModelItem item : list) {
                    if (monitor.isCanceled()) break;
                    if (item == null || item.getName() == null) continue;
                    if (!prefix.isEmpty() && !item.getName().toLowerCase(java.util.Locale.ROOT).contains(prefix)) continue;
                    monitor.subTask(type.getTypeName() + ": " + item.getName());
                    try {
                        IModelObject obj = store.getObject(type, item.getName());
                        if (obj == null) { failures.add("Missing definition: " + type.getTypeName()); continue; }
                        entries.add(WorkflowExtractor.extract(item, obj));
                    } catch (Exception ex) {
                        // No object names or sensitive values in persistent logs.
                        if (failures.size() < 100)
                            failures.add(type.getTypeName() + ": failed to read " + ex.getClass().getSimpleName());
                    }
                    monitor.worked(1);
                }
            }
        } finally { monitor.done(); }
        if (monitor.isCanceled()) failures.add("Cancelled by user");
        return new SearchIndex<>(store.getName(), store.getUser(), overlay, entries,
                failures.isEmpty(), failures);
    }
}
