import java.lang.reflect.Proxy;
import java.util.List;
import com.bmc.arsys.api.Constants;
import com.bmc.arsys.api.RunProcessAction;
import com.bmc.arsys.studio.model.ar.ARActiveLink;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import se.yrell.search.bmc.WorkflowExtractor;
import se.yrell.search.core.SearchIndex;

public final class WorkflowExtractorTest {
    public static void main(String[] args) {
        IModelItem item = (IModelItem) Proxy.newProxyInstance(IModelItem.class.getClassLoader(),
            new Class<?>[]{IModelItem.class}, (proxy, method, a) -> switch(method.getName()) {
                case "getName" -> "ACME:Send";
                case "getItemType" -> IARSystemTypes.ACTIVE_LINK;
                default -> null;
            });
        ARActiveLink link = new ARActiveLink();
        link.setFormList(List.of("ACME:Form"));
        link.setExecuteMask(Constants.AR_EXECUTE_ON_EVENT);
        link.setActionList(List.of(new RunProcessAction("PERFORM-ACTION-SEND-EVENT @ ChildClosed")));
        var extracted = WorkflowExtractor.extract(item, link);
        var index = new SearchIndex<>("test", "user", "", List.of(extracted), true, List.of());
        ok(index.search("ChildClosed", SearchIndex.Mode.EVENTS, 30).totalHits() == 1, "event send action");
        ok(index.search("On Event", SearchIndex.Mode.EVENTS, 30).totalHits() >= 1, "event receiver designation");
        ok(index.search("ACME:Form", SearchIndex.Mode.FORMS, 30).totalHits() == 1, "associated form");
        ok(index.search("RunProcess", SearchIndex.Mode.ACTIONS, 30).totalHits() >= 1, "action class");
        ok(index.search("ChildClosed", SearchIndex.Mode.ALL, 30).objects().size() == 1, "one export object");
        System.out.println("WorkflowExtractorTest: 5 PASS");
    }
    static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
