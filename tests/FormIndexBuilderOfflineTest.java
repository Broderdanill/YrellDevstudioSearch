import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import com.bmc.arsys.api.Timestamp;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
import com.bmc.arsys.studio.model.store.IModelObject;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import com.bmc.arsys.studio.model.type.IModelType;
import org.eclipse.core.runtime.IProgressMonitor;
import se.yrell.search.bmc.IndexBuilder;
import se.yrell.search.core.SearchIndex;

/**
 * IndexBuilder integration regression with API-neutral dynamic proxies.
 *
 * Unlike direct `implements IStore/IModelObject/IModelItem/IProgressMonitor` test fakes,
 * proxies do not rely on the set of abstract methods in one particular Developer Studio SDK.
 * The form-only metadata contract is declared as a separate public interface so the same
 * test can execute against both standalone offline stubs and actual BMC/Eclipse libraries.
 */
public final class FormIndexBuilderOfflineTest {
    public interface FieldsModel { List<Field> getFields(); }

    public static final class Field {
        private final int id;
        private final String name;
        public Field(int id, String name) { this.id = id; this.name = name; }
        public int getFieldID() { return id; }
        public String getName() { return name; }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler,
                               Class<?>... additional) {
        Class<?>[] interfaces = new Class<?>[additional.length + 1];
        interfaces[0] = type;
        System.arraycopy(additional, 0, interfaces, 1, additional.length);
        return (T) Proxy.newProxyInstance(FormIndexBuilderOfflineTest.class.getClassLoader(), interfaces,
                (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "Test proxy: " + type.getSimpleName();
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == args[0];
                            default -> null;
                        };
                    }
                    return handler.invoke(instance, method, args);
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short)0;
        if (type == byte.class) return (byte)0;
        if (type == char.class) return (char)0;
        return null;
    }

    private static IModelObject definition(String name) {
        return proxy(IModelObject.class, (self, method, args) ->
                method.getName().equals("getFields")
                        ? List.of(new Field(22, name)) : defaultValue(method.getReturnType()),
                FieldsModel.class);
    }

    private static IModelItem item(IModelType type, String name) {
        return proxy(IModelItem.class, (self, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "getItemType" -> type;
            case "getLastUpdateTime" -> new Timestamp(1234);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static final class StoreState {
        int formGets;
        int workflowGets;
        boolean changed;

        IStore store() {
            return proxy(IStore.class, (self, method, args) -> switch (method.getName()) {
                case "isConnected" -> true;
                case "getName" -> "AR Server";
                case "getUser" -> "Demo";
                // BMC IStore.getSupportedTypes() returns Set, not Collection.
                case "getSupportedTypes" -> Set.of(IARSystemTypes.FORM, IARSystemTypes.FILTER);
                case "getList" -> {
                    IModelType type = (IModelType) args[0];
                    ItemList<IModelItem> list = new ItemList<>();
                    list.addItem(item(type, type == IARSystemTypes.FORM
                            ? "HPD:Help Desk" : "FLX:Filter"));
                    yield list;
                }
                case "getObject" -> {
                    if (args[0] == IARSystemTypes.FORM) {
                        formGets++;
                        yield definition(changed ? "Newest Field" : "Old Field");
                    }
                    workflowGets++;
                    yield definition("Unused Workflow Field");
                }
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static IProgressMonitor monitor() {
        return proxy(IProgressMonitor.class, (self, method, args) ->
                defaultValue(method.getReturnType()));
    }

    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        StoreState state = new StoreState();
        IStore store = state.store();
        var first = IndexBuilder.build(store, "", null, monitor());
        ok(first.index().size() == 2, "indexed form + filter");
        ok(first.index().complete(), "complete original");
        ok(first.index().search("fieldname:\"Old Field\"", SearchIndex.Mode.ALL,100)
                .objects().size() == 1, "original field searchable");

        state.changed = true;
        var changed = IndexBuilder.build(store, "", first.index(), monitor());
        ok(changed.reused() == 1 && changed.fetched() == 1,
                "workflow reused but form reread");
        ok(state.formGets == 2 && state.workflowGets == 1,
                "form fetched twice but workflow only once");
        ok(changed.index().search("fieldname:\"Newest Field\"", SearchIndex.Mode.ALL,100)
                .objects().size() == 1, "latest field searchable");
        ok(changed.index().search("fieldname:\"Old Field\"", SearchIndex.Mode.ALL,100)
                .objects().isEmpty(), "stale field removed");
        System.out.println("FormIndexBuilderOfflineTest: 7 PASS");
    }
}
