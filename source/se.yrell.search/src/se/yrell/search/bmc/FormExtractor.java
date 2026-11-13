package se.yrell.search.bmc;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.store.IModelObject;
import com.bmc.arsys.studio.model.store.IStore;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.core.SearchIndex.Confidence;
import se.yrell.search.core.SearchIndex.Occurrence;

/**
 * Searches saved AR form/field DEFINITIONS, not form records or currently open unsaved editors.
 * BMC builds expose the ARServerUser through different studio-model adapters. We deliberately
 * discover only named read-only public metadata methods rather than depending on a private
 * BMC implementation type. An inaccessible field catalogue is reported as a partial index.
 */
public final class FormExtractor {
    private static final int MAX_FIELDS = 20_000;
    private static final int MAX_OCCURRENCES = 120_000;
    private static final String[] FIELD_LIST = {"getFieldObjects", "getFieldList",
            "getFields", "getAllFields", "getFieldDefinitions", "getFormFields", "getFieldInfoList",
            "getFieldMap", "getFieldsMap", "getFieldObjectList", "getFieldsList"};
    private static final String[] FORM_ACCESS = {"getForm", "getARForm", "getFormDefinition",
            "getDefinition", "getUnderlyingForm"};
    private static final String[] SERVER_ACCESS = {"getARServerUser", "getArServerUser", "getServerUser",
            "getARServer", "getArServer", "getServer", "getConnection", "getSession", "getContext",
            "getApiContext", "getARServerContext", "getServerInstance"};
    private FormExtractor() {}

    public record Metadata(List<Occurrence> occurrences, int fieldCount, boolean complete, String problem) {
        public Metadata { occurrences = List.copyOf(occurrences); }
    }
    public record Extracted(SearchIndex.Entry<IModelItem> entry, int fields, boolean complete, String problem) {}

    public static Extracted extract(IModelItem item, IModelObject definition, IStore store) {
        Metadata metadata = metadata(item.getName(), definition, store);
        return new Extracted(new SearchIndex.Entry<>(item.getItemType().getTypeName(), item.getName(), item,
                metadata.occurrences()), metadata.fieldCount(), metadata.complete(), metadata.problem());
    }

    /** Package-agnostic metadata path also used by offline regression tests. */
    public static Metadata metadata(String formName, Object definition, Object store) {
        List<Occurrence> occurrences = new ArrayList<>();
        add(occurrences, "Form definition", "Form", formName);
        property(occurrences, "Form type", "Form", definition, "getFormType", "getSchemaType");
        property(occurrences, "Form description", "Form", definition, "getDescription");
        property(occurrences, "Form help", "Form", definition, "getHelpText", "getHelp");
        FieldSource source = fields(formName, definition, store);
        if (source.fields() == null) return new Metadata(occurrences, 0, false,
                "Form field catalogue unavailable (no supported BMC field-list API)" +
                (source.error().isBlank() ? "" : ": " + source.error()));
        int count = 0;
        boolean incompleteFields = false;
        Set<String> seen = new java.util.HashSet<>();
        for (Object field : source.fields()) {
            if (field == null) continue;
            if (count >= MAX_FIELDS || occurrences.size() >= MAX_OCCURRENCES) {
                return new Metadata(occurrences, count, false, "Form field catalogue exceeded safety limit");
            }
            String id = read(field, "getFieldID", "getFieldId", "getFieldIdentifier", "getId");
            String name = read(field, "getName", "getFieldName");
            if (id.isBlank() || name.isBlank()) incompleteFields = true;
            if (id.isBlank() && name.isBlank()) continue;
            if (!seen.add((id.isBlank() ? "name:" + name : "id:" + id))) continue;
            count++;
            String location = "Field " + (id.isBlank() ? "?" : id) +
                    (name.isBlank() ? "" : " · " + name);
            add(occurrences, "Field ID", location, id);
            add(occurrences, "Field name", location, name);
            property(occurrences, "Field label", location, field,
                    "getDisplayLabel", "getLabel", "getFieldLabel");
            property(occurrences, "Field data type", location, field, "getDataType", "getDataTypeName");
            property(occurrences, "Field type", location, field, "getFieldType");
            property(occurrences, "Field menu", location, field, "getMenuName", "getCharMenu");
            property(occurrences, "Field help", location, field, "getHelpText", "getHelp");
            property(occurrences, "Field default", location, field, "getDefaultValue");
            property(occurrences, "Field option", location, field, "getOption", "getFieldOption");
            // A view-specific label is an optional extra; never replace the actual field name with it.
            Object views = first(field, "getViewNames", "getViews");
            if (views instanceof Iterable<?> list) for (Object view : list) {
                if (view instanceof String nameOfView) add(occurrences, "Field view", location, nameOfView);
            }
        }
        if (!source.fields().isEmpty() && count == 0)
            return new Metadata(occurrences, 0, false, "Field records returned without readable names or IDs");
        return new Metadata(occurrences, count, source.complete() && !incompleteFields,
                incompleteFields ? "Some fields have missing IDs or names" : source.error());
    }

    private record FieldSource(List<?> fields, boolean complete, String error) {}
    private static FieldSource fields(String formName, Object definition, Object store) {
        String error = "";
        // Preferred: ARServerUser.getListFieldObjects(formName), returns all server fields,
        // including fields not placed on any view.
        List<Object> providers = new ArrayList<>();
        if (store != null) providers.add(store);
        if (definition != null) providers.add(definition);
        // At most two adapter hops: store -> connection -> ARServerUser.
        // The identity set prevents recursive discovery and unrelated object-graph traversal.
        java.util.Set<Object> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0; i < providers.size() && i < 18; i++) {
            Object base = providers.get(i);
            if (!visited.add(base)) continue;
            for (String method : SERVER_ACCESS) {
                try {
                    Object service = call(base, method);
                    if (service != null && service != base && !visited.contains(service) &&
                            !containsIdentity(providers,service) && providers.size()<18) providers.add(service);
                } catch (MetadataFailure ignored) { /* Try remaining adapter methods. */ }
            }
        }
        for (Object provider : providers) {
            try {
                Object value = callWith(provider, "getListFieldObjects", formName);
                if (value != null) {
                    List<?> list = unpack(value);
                    if (list != null) return hydrate(list, formName, providers);
                }
            } catch (MetadataFailure ex) { error = ex.getMessage(); }
        }
        // BMC form model adapters may instead expose all fields on the Form model
        // or a single underlying Form value object; never enumerate arbitrary store fields.
        List<Object> models = new ArrayList<>();
        if (definition != null) models.add(definition);
        for (int i = 0; i < models.size() && i < 5; i++) {
            Object model = models.get(i);
            for (String method : FORM_ACCESS) {
                try {
                    Object nested = call(model, method);
                    if (nested != null && nested != model && !containsIdentity(models,nested) && models.size() < 5)
                        models.add(nested);
                } catch (MetadataFailure ignored) { /* Try another public Form adapter. */ }
            }
        }
        for (Object model : models) for (String method : FIELD_LIST) {
            try {
                Object value = call(model, method);
                List<?> list = unpack(value);
                if (list != null) return hydrate(list, formName, providers);
            } catch (MetadataFailure ex) { error = ex.getMessage(); }
        }
        return new FieldSource(null, false, error);
    }
    private static boolean containsIdentity(List<Object> values, Object candidate) {
        for (Object value: values) if (value == candidate) return true;
        return false;
    }

    private static FieldSource hydrate(List<?> list, String formName, List<Object> providers) {
        if (list.isEmpty()) return new FieldSource(list, true, "");
        if (!(list.get(0) instanceof Number)) return new FieldSource(list, true, "");
        // A field ID list alone is insufficient to search name/type. Resolve each ID.
        List<Object> resolved = new ArrayList<>();
        for (Object id : list) {
            if (!(id instanceof Number number)) continue;
            Object field = null;
            for (Object provider : providers) {
                try { field = callWith(provider, "getField", formName, number.intValue()); }
                catch (MetadataFailure ignored) { /* Try another provider. */ }
                if (field != null) break;
            }
            if (field == null) return new FieldSource(null, false,
                    "Field IDs returned but field definitions could not be retrieved");
            resolved.add(field);
        }
        return new FieldSource(resolved, true, "");
    }

    private static List<?> unpack(Object value) {
        if (value instanceof List<?> list) return list;
        if (value instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object obj : iterable) {
                if (list.size() >= MAX_FIELDS + 1) break;
                list.add(obj);
            }
            return list;
        }
        if (value instanceof Map<?, ?> map) return new ArrayList<>(map.values());
        if (value != null && value.getClass().isArray()) {
            int n = Math.min(Array.getLength(value), MAX_FIELDS + 1);
            List<Object> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(Array.get(value, i));
            return list;
        }
        return null;
    }

    private static Object first(Object target, String... methods) {
        for (String method : methods) {
            Object value = call(target, method);
            if (value != null) return value;
        }
        return null;
    }
    private static String read(Object target, String... methods) {
        Object value = first(target, methods);
        if (value == null) return "";
        if (value instanceof String || value instanceof Number || value instanceof Enum<?> || value instanceof Boolean)
            return String.valueOf(value);
        return ""; // Avoid object.toString() of arbitrary BMC models (could include data/secrets).
    }
    private static void property(List<Occurrence> out, String category, String location,
                                 Object object, String... methods) {
        try { add(out, category, location, read(object, methods)); }
        catch (MetadataFailure ignored) { /* Optional metadata must not hide a form. */ }
    }
    private static void add(List<Occurrence> out, String category, String location, String value) {
        if (value == null || value.isBlank() || out.size() >= MAX_OCCURRENCES) return;
        if (value.length() > 4_000) value = value.substring(0, 4_000) + " [TRUNCATED]";
        out.add(new Occurrence(category, location, value, Confidence.EXACT));
    }
    private static final class MetadataFailure extends RuntimeException {
        MetadataFailure(String description) { super(description); }
    }
    private static Object call(Object on, String method) {
        return callWith(on, method);
    }
    private static Object callWith(Object on, String name, Object... args) {
        if (on == null) return null;
        for (Method method : on.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] params = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> type = params[i];
                Object arg = args[i];
                if (arg == null) { compatible = !type.isPrimitive(); }
                else if (type.isPrimitive()) compatible =
                        (type == int.class && arg instanceof Integer) || (type == long.class && arg instanceof Long);
                else compatible = type.isInstance(arg);
                if (!compatible) break;
            }
            if (!compatible) continue;
            try { return method.invoke(on, args); }
            catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
                Throwable actual = ex instanceof InvocationTargetException ite ? ite.getTargetException() : ex;
                throw new MetadataFailure(name + " failed (" + actual.getClass().getSimpleName() + ")");
            }
        }
        return null;
    }
}
