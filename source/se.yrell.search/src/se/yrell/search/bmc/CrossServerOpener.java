package se.yrell.search.bmc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.runtime.IProgressMonitor;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IModelType;

/**
 * Resolves target-server items by exact (type,name); never reuses source-server IModelItem.
 * Caller must run off the SWT UI thread, and open resolved items on the UI thread.
 */
public final class CrossServerOpener {
    public record ObjectKey(IModelType type, String name) {
        public ObjectKey {
            if (type == null || name == null || name.isBlank()) throw new IllegalArgumentException("Object type/name missing");
        }
        public String description() { return type.getTypeName() + " · " + name; }
    }
    public record Resolution(List<IModelItem> resolved, List<String> missing, List<String> errors) {
        public Resolution {
            resolved = List.copyOf(resolved);
            missing = List.copyOf(missing);
            errors = List.copyOf(errors);
        }
    }
    private CrossServerOpener() {}
    public static List<ObjectKey> keys(List<IModelItem> source) {
        Map<String,ObjectKey> keys = new LinkedHashMap<>();
        for (IModelItem item:source) {
            if (item == null || item.getName() == null || item.getItemType() == null) continue;
            ObjectKey key = new ObjectKey(item.getItemType(),item.getName());
            keys.putIfAbsent(key.type().getTypeName() + "\u0000" + key.name(),key);
        }
        return List.copyOf(keys.values());
    }
    public static Resolution resolve(IStore target, List<ObjectKey> selected, IProgressMonitor monitor) {
        if (target == null || !target.isConnected()) throw new IllegalArgumentException("Target AR server is not connected");
        Map<IModelType,List<ObjectKey>> byType = new LinkedHashMap<>();
        for (ObjectKey item:selected) byType.computeIfAbsent(item.type(), k -> new ArrayList<>()).add(item);
        List<IModelItem> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        monitor.beginTask("Resolve definitions on " + target.getName(), selected.size());
        try {
            for (Map.Entry<IModelType,List<ObjectKey>> group:byType.entrySet()) {
                if (monitor.isCanceled()) break;
                IModelType type = group.getKey();
                if (!target.isConnected()) { errors.add("Target server disconnected"); break; }
                if (!target.getSupportedTypes().contains(type)) {
                    for (ObjectKey k:group.getValue()) missing.add(k.description() + " (unsupported type)");
                    monitor.worked(group.getValue().size());
                    continue;
                }
                try {
                    // BMC's name-scoped item list avoids listing an entire large object type.
                    List<String> names = group.getValue().stream().map(ObjectKey::name).toList();
                    ItemList<IModelItem> items = target.getItemList(type,names);
                    Map<String,IModelItem> exact = new LinkedHashMap<>();
                    if (items != null) for (IModelItem item:items) {
                        if (item != null && item.getStore() == target && item.getItemType().equals(type))
                            exact.putIfAbsent(item.getName(),item);
                    }
                    for (ObjectKey k:group.getValue()) {
                        IModelItem match = exact.get(k.name());
                        if (match == null) missing.add(k.description());
                        else found.add(match);
                        monitor.worked(1);
                    }
                } catch (Exception ex) {
                    errors.add(type.getTypeName() + ": " + ex.getClass().getSimpleName() +
                            (ex.getMessage() == null ? "" : " – " + ex.getMessage()));
                    monitor.worked(group.getValue().size());
                }
            }
        } finally { monitor.done(); }
        return new Resolution(found, missing, errors);
    }
}
