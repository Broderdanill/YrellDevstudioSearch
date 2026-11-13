package se.yrell.search.bmc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.eclipse.core.runtime.IProgressMonitor;
import com.bmc.arsys.api.Timestamp;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
import com.bmc.arsys.studio.model.store.IARServerStore;
import com.bmc.arsys.studio.model.store.IModelObject;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import com.bmc.arsys.studio.model.type.IModelType;
import se.yrell.search.core.SearchIndex;

/** Background-only definition indexing. Incremental mode reuses only timestamp-verified unchanged objects. */
public final class IndexBuilder {
    private static final List<IModelType> TYPES = List.of(IARSystemTypes.ACTIVE_LINK,
            IARSystemTypes.FILTER, IARSystemTypes.ESCALATION,
            IARSystemTypes.ACTIVE_LINK_GUIDE, IARSystemTypes.FILTER_GUIDE,
            IARSystemTypes.FORM);
    private IndexBuilder() {}
    public record BuildResult(SearchIndex<IModelItem> index, int reused, int fetched) {}
    public static SearchIndex<IModelItem> build(IStore store, String nameFilter, IProgressMonitor monitor) {
        return build(store,nameFilter,null,monitor).index();
    }
    public static BuildResult build(IStore store, String nameFilter, SearchIndex<IModelItem> previous,
                                    IProgressMonitor monitor) {
        List<SearchIndex.Entry<IModelItem>> entries = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        String prefix = nameFilter == null ? "" : nameFilter.strip().toLowerCase(Locale.ROOT);
        String overlay = store instanceof IARServerStore ar ? String.valueOf(ar.getOverlayGroup()) : "";
        boolean canReuse = previous != null && previous.complete() && prefix.isEmpty() &&
                Objects.equals(previous.server(),store.getName()) && Objects.equals(previous.user(),store.getUser()) &&
                Objects.equals(previous.overlay(),overlay);
        Map<String,SearchIndex.Entry<IModelItem>> old = new HashMap<>();
        if (canReuse) for (SearchIndex.Entry<IModelItem> e:previous.entries()) old.put(key(e.type(),e.name()),e);
        int reused=0,fetched=0;
        monitor.beginTask(canReuse?"Refresh changed AR definitions":"Index saved AR forms and workflow definitions", IProgressMonitor.UNKNOWN);
        try {
            for (IModelType type : TYPES) {
                if (monitor.isCanceled()) break;
                if (!store.isConnected()) { problems.add("Connection lost; index incomplete"); break; }
                if (!store.getSupportedTypes().contains(type)) continue;
                monitor.subTask("Listing " + type.getTypeName());
                ItemList<IModelItem> list;
                try { list = store.getList(type,false); }
                catch (Exception ex) { problems.add(type.getTypeName()+": could not list objects ("+ex.getClass().getSimpleName()+")"); continue; }
                if (list == null) { problems.add(type.getTypeName()+": returned no object list"); continue; }
                for (IModelItem item : list) {
                    if (monitor.isCanceled()) break;
                    if (item==null || item.getName()==null) continue;
                    if (!prefix.isEmpty() && !item.getName().toLowerCase(Locale.ROOT).contains(prefix)) continue;
                    monitor.subTask(type.getTypeName()+": "+item.getName());
                    try {
                        SearchIndex.Entry<IModelItem> prior = old.get(key(type.getTypeName(),item.getName()));
                        // A form timestamp does not necessarily change when an individual field is updated.
                        // Always fetch form+field metadata even when the form timestamp is unchanged.
                        if (type != IARSystemTypes.FORM && prior!=null && timestampEqual(prior.object(),item)) {
                            entries.add(new SearchIndex.Entry<>(prior.type(),prior.name(),item,prior.occurrences()));
                            reused++;
                        } else {
                            IModelObject definition=store.getObject(type,item.getName());
                            if (definition==null) {
                                if (problems.size()<100) problems.add(type.getTypeName()+": null definition");
                            } else {
                                if (type == IARSystemTypes.FORM) {
                                    FormExtractor.Extracted form = FormExtractor.extract(item,definition,store);
                                    entries.add(form.entry());
                                    if (!form.complete() && problems.size()<100)
                                        problems.add("Form " + item.getName() + ": " + form.problem());
                                } else entries.add(WorkflowExtractor.extract(item,definition));
                                fetched++;
                            }
                        }
                    } catch (Exception ex) {
                        if (problems.size()<100) problems.add(type.getTypeName()+": failed to read ("+ex.getClass().getSimpleName()+")");
                    }
                    monitor.worked(1);
                }
            }
        } finally { monitor.done(); }
        if (!prefix.isEmpty()) problems.add("Name prefilter active: other names intentionally excluded");
        if (monitor.isCanceled()) problems.add("Cancelled by user");
        return new BuildResult(new SearchIndex<>(store.getName(),store.getUser(),overlay,entries,
                problems.isEmpty(),problems),reused,fetched);
    }
    /** Null timestamps are unsafe to reuse. Object metadata must be from the same server and overlay. */
    public static boolean timestampEqual(IModelItem oldItem,IModelItem newItem) {
        if (oldItem==null || newItem==null) return false;
        try {
            Timestamp a=oldItem.getLastUpdateTime(), b=newItem.getLastUpdateTime();
            return a!=null && b!=null && Objects.equals(a,b);
        } catch (RuntimeException ex) { return false; }
    }
    private static String key(String type,String name) { return type+"\u0000"+name; }
}
