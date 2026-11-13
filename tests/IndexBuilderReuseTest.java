import java.lang.reflect.Proxy;
import com.bmc.arsys.api.Timestamp;
import com.bmc.arsys.studio.model.item.IModelItem;
import se.yrell.search.bmc.IndexBuilder;

public final class IndexBuilderReuseTest {
    static IModelItem item(Timestamp ts) {
        return (IModelItem) Proxy.newProxyInstance(IModelItem.class.getClassLoader(),new Class[]{IModelItem.class},
            (proxy, method, args)-> method.getName().equals("getLastUpdateTime") ? ts : null);
    }
    public static void main(String[] args) {
        ok(IndexBuilder.timestampEqual(item(new Timestamp(100)),item(new Timestamp(100))),"same timestamp reusable");
        ok(!IndexBuilder.timestampEqual(item(new Timestamp(100)),item(new Timestamp(101))),"modified object fetched");
        ok(!IndexBuilder.timestampEqual(item(null),item(new Timestamp(100))),"null timestamp not reusable");
        ok(!IndexBuilder.timestampEqual(item(new Timestamp(100)),item(null)),"missing timestamp not reusable");
        System.out.println("IndexBuilderReuseTest: 4 PASS");
    }
    static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
}
