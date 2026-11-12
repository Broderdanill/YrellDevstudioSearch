package se.yrell.search.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable, per-connection in-memory index. Objects are AR definitions, not individual actions. */
public final class SearchIndex<T> {
    public enum Mode { ALL, EVENTS, ACTIONS, FIELDS, FORMS, REFERENCES }
    public enum Confidence { EXACT, POSSIBLE, TEXT }
    public record Occurrence(String category, String location, String value, Confidence confidence) {
        public Occurrence {
            Objects.requireNonNull(category);
            Objects.requireNonNull(location);
            Objects.requireNonNull(value);
            Objects.requireNonNull(confidence);
        }
    }
    public record Entry<T>(String type, String name, T object, List<Occurrence> occurrences) {
        public Entry { occurrences = List.copyOf(occurrences); }
    }
    public record Hit<T>(Entry<T> entry, Occurrence occurrence) {}
    public record Result<T>(List<Hit<T>> hits, List<Entry<T>> objects, int totalHits, boolean truncated) {}
    private final String server, user, overlay;
    private final List<Entry<T>> entries;
    private final boolean complete;
    private final List<String> problems;

    public SearchIndex(String server, String user, String overlay, List<Entry<T>> entries,
                       boolean complete, List<String> problems) {
        this.server = server;
        this.user = user;
        this.overlay = overlay;
        this.entries = List.copyOf(entries);
        this.complete = complete;
        this.problems = List.copyOf(problems);
    }
    public String server() { return server; }
    public String user() { return user; }
    public String overlay() { return overlay; }
    public List<Entry<T>> entries() { return entries; }
    public boolean complete() { return complete; }
    public List<String> problems() { return problems; }
    public int size() { return entries.size(); }

    public Result<T> search(String phrase, Mode mode, int limit) {
        String query = phrase == null ? "" : phrase.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return new Result<>(List.of(), List.of(), 0, false);
        int maximum = Math.max(1, limit);
        List<Hit<T>> hits = new ArrayList<>();
        Map<String, Entry<T>> objects = new LinkedHashMap<>();
        int total = 0;
        for (Entry<T> entry : entries) {
            for (Occurrence occurrence : entry.occurrences()) {
                if (!matchesMode(mode, occurrence.category())) continue;
                if (!occurrence.value().toLowerCase(Locale.ROOT).contains(query)) continue;
                total++;
                if (hits.size() >= maximum) continue;
                hits.add(new Hit<>(entry, occurrence));
                objects.putIfAbsent(entry.type() + "\u0000" + entry.name(), entry);
            }
        }
        return new Result<>(Collections.unmodifiableList(hits),
                List.copyOf(objects.values()), total, total > hits.size());
    }
    public static boolean matchesMode(Mode mode, String category) {
        if (mode == Mode.ALL) return true;
        return switch (mode) {
            case EVENTS -> category.equals("Event sender") || category.equals("Event receiver");
            case ACTIONS -> category.equals("Action") || category.equals("Run Process") || category.equals("Target form");
            case FIELDS -> category.equals("Field reference") || category.equals("Qualification") || category.equals("Assignment");
            case FORMS -> category.equals("Form") || category.equals("Target form");
            case REFERENCES -> category.equals("Guide reference") || category.equals("Target form") || category.equals("Form");
            default -> true;
        };
    }
}
