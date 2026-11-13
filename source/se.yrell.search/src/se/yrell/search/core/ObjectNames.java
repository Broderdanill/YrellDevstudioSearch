package se.yrell.search.core;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Clipboard payloads for selected definition names; preserve selection order, remove repeated hits. */
public final class ObjectNames {
    private ObjectNames() {}

    public static String lines(Collection<String> names) {
        Objects.requireNonNull(names);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String name : names) if (name != null && !name.isBlank()) unique.add(name);
        return String.join(System.lineSeparator(), unique);
    }

    public static int uniqueCount(Collection<String> names) {
        return (int) names.stream().filter(Objects::nonNull).filter(n -> !n.isBlank()).distinct().count();
    }
}
