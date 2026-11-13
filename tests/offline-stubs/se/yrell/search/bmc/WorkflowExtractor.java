package se.yrell.search.bmc;
import java.util.List;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.store.IModelObject;
import se.yrell.search.core.SearchIndex;
/** Test-only BMC workflow extractor stand-in; never included in release JAR. */
public final class WorkflowExtractor {
    public static SearchIndex.Entry<IModelItem> extract(IModelItem item, IModelObject definition) {
        return new SearchIndex.Entry<>(item.getItemType().getTypeName(),item.getName(),item,
            List.of(new SearchIndex.Occurrence("Action","If Action 1","SetFields",SearchIndex.Confidence.EXACT)));
    }
}
