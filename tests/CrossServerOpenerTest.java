import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.eclipse.core.runtime.NullProgressMonitor;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IModelType;
import se.yrell.search.bmc.CrossServerOpener;

public final class CrossServerOpenerTest {
  private static int passed;
  private static void check(boolean ok, String what) { if(!ok) throw new AssertionError(what); passed++; }
  private static <T> T proxy(Class<T> iface, java.lang.reflect.InvocationHandler handler) {
    return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler));
  }
  public static void main(String[] args) {
    IModelType filter = proxy(IModelType.class,(p,m,a)->identity(p,m,a,"Filter"));
    IModelType active = proxy(IModelType.class,(p,m,a)->identity(p,m,a,"Active Link"));
    IStore[] holder=new IStore[1];
    IStore target=proxy(IStore.class,(p,m,a)->switch(m.getName()) {
      case "isConnected" -> true;
      case "getName" -> "target";
      case "getSupportedTypes" -> Set.of(filter);
      case "getItemList" -> {
        @SuppressWarnings("unchecked") List<String> names=(List<String>)a[1];
        ItemList<IModelItem> values=new ItemList<>();
        for (String name:names) if(name.equals("F:Exists")) values.addItem(item(filter,name,holder[0]));
        // This extra item must never appear in the resolution.
        values.addItem(item(filter,"F:Unexpected",holder[0]));
        yield values;
      }
      default -> defaultValue(m.getReturnType());
    });
    holder[0]=target;
    List<CrossServerOpener.ObjectKey> keys=List.of(
        new CrossServerOpener.ObjectKey(filter,"F:Exists"),
        new CrossServerOpener.ObjectKey(filter,"F:Missing"),
        new CrossServerOpener.ObjectKey(active,"AL:Exists"));
    CrossServerOpener.Resolution resolved=CrossServerOpener.resolve(target,keys,new NullProgressMonitor());
    check(resolved.resolved().size()==1,"exact matching target items only");
    check(resolved.resolved().get(0).getStore()==target,"target store identity");
    check(resolved.missing().size()==2,"missing and unsupported reported");
    check(resolved.errors().isEmpty(),"no errors");
    List<CrossServerOpener.ObjectKey> dedup=CrossServerOpener.keys(List.of(item(filter,"F:Exists",target),item(filter,"F:Exists",target),item(active,"F:Exists",target)));
    check(dedup.size()==2,"key dedup by type and name");
    System.out.println("CrossServerOpenerTest: " + passed + " PASS");
  }
  private static IModelItem item(IModelType type,String name,IStore store) {
    return proxy(IModelItem.class,(p,m,a)->switch(m.getName()) {
      case "getName" -> name;
      case "getStore" -> store;
      case "getItemType" -> type;
      default -> defaultValue(m.getReturnType());
    });
  }
  private static Object identity(Object p,java.lang.reflect.Method m,Object[] a,String typeName) {
    return switch(m.getName()) {
      case "equals" -> p==a[0];
      case "hashCode" -> System.identityHashCode(p);
      case "getTypeName" -> typeName;
      default -> defaultValue(m.getReturnType());
    };
  }
  private static Object defaultValue(Class<?> type) {
    if (type==boolean.class) return false;
    if (type==int.class) return 0;
    if (type==long.class) return 0L;
    return null;
  }
}
