package se.yrell.search.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable in-memory index. Individual occurrences can be truncated on screen; the export object set never is. */
public final class SearchIndex<T> {
    public enum Mode { ALL, EVENTS, ACTIONS, FIELDS, FORMS, REFERENCES, FORM_DETAILS }
    public enum Confidence { EXACT, POSSIBLE, TEXT }
    public record Occurrence(String category, String location, String value, Confidence confidence) {
        public Occurrence {
            Objects.requireNonNull(category); Objects.requireNonNull(location);
            Objects.requireNonNull(value); Objects.requireNonNull(confidence);
        }
    }
    public record Entry<T>(String type, String name, T object, List<Occurrence> occurrences) {
        public Entry { occurrences = List.copyOf(occurrences); }
    }
    public record Hit<T>(Entry<T> entry, Occurrence occurrence) {}
    public record Result<T>(List<Hit<T>> hits, List<Entry<T>> objects, Map<String,Integer> objectCounts,
                            int totalHits, boolean truncated, String queryError) {
        public Result {
            hits = List.copyOf(hits); objects = List.copyOf(objects);
            objectCounts = Collections.unmodifiableMap(new LinkedHashMap<>(objectCounts));
        }
        public int hitsFor(Entry<T> entry) { return objectCounts.getOrDefault(key(entry), 0); }
    }
    private final String server, user, overlay;
    private final List<Entry<T>> entries;
    private final boolean complete;
    private final List<String> problems;

    public SearchIndex(String server, String user, String overlay, List<Entry<T>> entries,
                       boolean complete, List<String> problems) {
        this.server = server; this.user = user; this.overlay = overlay;
        this.entries = List.copyOf(entries); this.complete = complete; this.problems = List.copyOf(problems);
    }
    public String server() { return server; }
    public String user() { return user; }
    public String overlay() { return overlay; }
    public List<Entry<T>> entries() { return entries; }
    public boolean complete() { return complete; }
    public List<String> problems() { return problems; }
    public int size() { return entries.size(); }
    private static <T> String key(Entry<T> e) { return e.type() + "\u0000" + e.name(); }

    public Result<T> search(String phrase, Mode mode, int limit) {
        return search(phrase, mode, Set.of(), limit);
    }
    /** Allowed object types is a live search filter, independent of what was previously indexed. */
    public Result<T> search(String phrase, Mode mode, Set<String> allowedTypes, int limit) {
        SearchQuery query = SearchQuery.parse(phrase);
        if (query.error() != null || query.empty())
            return new Result<>(List.of(), List.of(), Map.of(), 0, false, query.error());
        final int maximum = Math.max(1, limit);
        List<Hit<T>> shown = new ArrayList<>();
        Map<String,Entry<T>> objects = new LinkedHashMap<>();
        Map<String,Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (Entry<T> entry : entries) {
            if (!allowedTypes.isEmpty() && !allowedTypes.contains(entry.type())) continue;
            // OR groups are evaluated per object. All positive terms in an AND group must be present.
            LinkedHashSet<Occurrence> matching = new LinkedHashSet<>();
            boolean accepted = false;
            for (List<SearchQuery.Term> group : query.groups()) {
                LinkedHashSet<Occurrence> groupMatches = new LinkedHashSet<>();
                boolean groupAccepted = true;
                for (SearchQuery.Term term : group) {
                    List<Occurrence> matchingTerm = matches(entry, term, mode);
                    if (term.negative()) {
                        if (!matchingTerm.isEmpty()) { groupAccepted = false; break; }
                    } else if (matchingTerm.isEmpty()) { groupAccepted = false; break; }
                    else groupMatches.addAll(matchingTerm);
                }
                if (groupAccepted) { accepted = true; matching.addAll(groupMatches); }
            }
            if (!accepted) continue;
            if (matching.isEmpty()) matching.add(new Occurrence("Name", "Object", entry.name(), Confidence.EXACT));
            String key = key(entry);
            objects.putIfAbsent(key, entry);
            counts.put(key, matching.size());
            for (Occurrence o : matching) {
                total++;
                if (shown.size() < maximum) shown.add(new Hit<>(entry, o));
            }
        }
        return new Result<>(shown, List.copyOf(objects.values()), counts, total,
                total > shown.size(), null);
    }
    private static <T> List<Occurrence> matches(Entry<T> entry, SearchQuery.Term term, Mode mode) {
        String value = term.value();
        if (term.field().equals("type")) {
            return entry.type().toLowerCase(Locale.ROOT).equals(value) ?
                    List.of(new Occurrence("Type", "Object", entry.type(), Confidence.EXACT)) : List.of();
        }
        if (term.field().equals("name")) {
            return entry.name().toLowerCase(Locale.ROOT).contains(value) ?
                    List.of(new Occurrence("Name", "Object", entry.name(), Confidence.EXACT)) : List.of();
        }
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence o : entry.occurrences()) {
            if (!matchesMode(mode, o.category())) continue;
            if (!inField(term.field(), o)) continue;
            String haystack = term.field().equals("where") || term.field().equals("branch") ? o.location() :
                    term.field().equals("category") ? o.category() : o.value();
            // Numeric Field IDs are identities, not substrings (7 must not match 17).
            boolean match = term.field().equals("fieldid") ? haystack.trim().equalsIgnoreCase(value) :
                    haystack.toLowerCase(Locale.ROOT).contains(value);
            if (match) result.add(o);
        }
        // Plain text always searches the object name, even if the active category filter is narrow.
        if (term.field().isEmpty() && entry.name().toLowerCase(Locale.ROOT).contains(value))
            result.add(new Occurrence("Name", "Object", entry.name(), Confidence.EXACT));
        return result;
    }
    private static boolean inField(String field, Occurrence o) {
        String cat = o.category().toLowerCase(Locale.ROOT);
        return switch (field) {
            case "", "category", "where", "branch" -> true;
            case "event" -> cat.startsWith("event");
            case "action" -> cat.equals("action") || cat.equals("action property") || cat.equals("run process");
            case "form" -> cat.equals("form") || cat.equals("target form") || cat.equals("service form") || cat.equals("form definition");
            case "field" -> cat.startsWith("field ") || cat.equals("field reference") ||
                    cat.equals("assignment") || cat.equals("qualification");
            case "fieldid" -> cat.equals("field id") || cat.equals("field reference");
            case "fieldname" -> cat.equals("field name");
            case "label" -> cat.equals("field label");
            case "datatype" -> cat.equals("field data type");
            case "fieldtype" -> cat.equals("field type");
            case "menu" -> cat.equals("field menu") || cat.equals("menu reference");
            case "view" -> cat.equals("field view") || cat.equals("target view");
            case "guide" -> cat.equals("guide reference") || cat.equals("guide call");
            case "process" -> cat.equals("run process") || cat.equals("process") || cat.equals("assignment");
            case "qual" -> cat.equals("qualification");
            case "ref" -> cat.equals("form") || cat.equals("target form") || cat.equals("guide reference") ||
                    cat.equals("guide call") || cat.equals("event receiver") || cat.equals("event sender");
            case "target" -> cat.equals("target form") || cat.equals("service form") || cat.equals("guide call");
            case "enabled" -> cat.equals("enabled");
            default -> false;
        };
    }
    public static boolean matchesMode(Mode mode, String category) {
        if (mode == Mode.ALL) return true;
        String c = category.toLowerCase(Locale.ROOT);
        return switch (mode) {
            case EVENTS -> c.startsWith("event");
            case ACTIONS -> c.equals("action") || c.equals("action property") || c.equals("run process") ||
                    c.equals("target form") || c.equals("assignment") || c.equals("guide call") || c.equals("service form");
            case FIELDS -> c.startsWith("field ") || c.equals("field reference") ||
                    c.equals("qualification") || c.equals("assignment");
            case FORMS -> c.startsWith("form ") || c.equals("form") ||
                    c.equals("target form") || c.equals("service form");
            case REFERENCES -> c.equals("guide reference") || c.equals("guide call") || c.equals("target form") ||
                    c.equals("service form") || c.equals("form") || c.startsWith("event");
            case FORM_DETAILS -> c.startsWith("form ") || c.startsWith("field ");
            default -> true;
        };
    }
}
