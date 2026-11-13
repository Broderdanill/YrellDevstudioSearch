package se.yrell.search.bmc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.bmc.arsys.api.*;
import se.yrell.search.core.SearchIndex;

/** Bounded, read-only traversal of BMC API value objects; no private fields or backend calls. */
final class ActionPropertyWalker {
    private static final int MAX_DEPTH = 5, MAX_NODES = 220, MAX_LIST = 80;
    private final List<SearchIndex.Occurrence> output;
    private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
    private int nodes;

    private ActionPropertyWalker(List<SearchIndex.Occurrence> output) { this.output = output; }
    static void inspect(Object obj, String path, List<SearchIndex.Occurrence> facts) {
        new ActionPropertyWalker(facts).walk(obj, path, 0);
    }
    private void add(String category, String location, String value) {
        if (value == null || value.isBlank() || nodes >= MAX_NODES) return;
        nodes++;
        if (value.length() > 16_000) value = value.substring(0, 16_000) + " [TRUNCATED]";
        output.add(new SearchIndex.Occurrence(category, location, value, SearchIndex.Confidence.TEXT));
    }
    private static String category(String path) {
        String key = path.toLowerCase(java.util.Locale.ROOT);
        if (key.contains("fieldid") || key.contains("fieldid") || key.endsWith(".field")) return "Field reference";
        if (key.contains("qualification") || key.contains("operand") || key.contains("run if") || key.contains("query")) return "Qualification";
        if (key.contains("assignment") || key.contains("mapping") || key.contains("fieldpairs")) return "Assignment";
        if (key.contains("guide")) return "Guide reference";
        if (key.contains("process") || key.contains("commandline")) return "Run Process";
        if (key.contains("form")) return "Target form";
        return "Action property";
    }
    private void walk(Object value, String path, int depth) {
        if (value == null || depth > MAX_DEPTH || nodes >= MAX_NODES) return;
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            add(category(path), path, String.valueOf(value)); return;
        }
        if (value instanceof Iterable<?> iterable) {
            int i = 0;
            for (Object el : iterable) {
                if (++i > MAX_LIST || nodes >= MAX_NODES) break;
                walk(el, path + "[" + i + "]", depth + 1);
            }
            return;
        }
        if (value instanceof Map<?, ?> || value.getClass().isArray()) return;
        if (!value.getClass().getName().startsWith("com.bmc.arsys.api.")) return;
        if (visited.put(value, Boolean.TRUE) != null) return;
        List<Method> getters = new ArrayList<>();
        for (Method m : value.getClass().getMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.getParameterCount() != 0 || m.getReturnType() == Void.TYPE) continue;
            String n = m.getName();
            if (!(n.startsWith("get") && n.length() > 3) && !(n.startsWith("is") && n.length() > 2)) continue;
            if (n.equals("getClass") || n.equals("getProperties") || n.equals("getAttributeMap") || n.equals("getChangeFlags") ||
                    n.equals("getAssignedGroup") || n.equals("getHashCode") || n.equals("getRawValue")) continue;
            getters.add(m);
        }
        getters.sort(Comparator.comparing(Method::getName));
        for (Method getter : getters) {
            if (nodes >= MAX_NODES) break;
            String n = getter.getName();
            String property = n.substring(n.startsWith("is") ? 2 : 3);
            try {
                Object child = getter.invoke(value);
                walk(child, path + " / " + property, depth + 1);
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* unsupported API getter */ }
        }
    }
}
