package se.yrell.search.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lightweight object-aware query: whitespace = AND, 'OR' = OR, -term = NOT, field:"quoted text". */
public final class SearchQuery {
    public record Term(String field, String value, boolean negative) {
        public Term {
            field = field == null ? "" : field.toLowerCase(Locale.ROOT);
            value = value == null ? "" : value.toLowerCase(Locale.ROOT);
        }
    }
    private static final Set<String> FIELDS = Set.of("type", "name", "event", "action", "form",
            "field", "guide", "process", "qual", "branch", "where", "category", "ref", "target", "enabled");
    private final List<List<Term>> groups;
    private final String error;

    private SearchQuery(List<List<Term>> groups, String error) {
        this.groups = groups.stream().map(List::copyOf).toList();
        this.error = error;
    }
    public List<List<Term>> groups() { return groups; }
    public String error() { return error; }
    public boolean empty() { return groups.stream().allMatch(List::isEmpty); }
    public boolean hasPositiveTerms() { return groups.stream().flatMap(List::stream).anyMatch(t -> !t.negative()); }

    public static SearchQuery parse(String input) {
        if (input == null || input.isBlank()) return new SearchQuery(List.of(List.of()), null);
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') { quoted = !quoted; continue; }
            if (!quoted && Character.isWhitespace(c)) {
                if (token.length() > 0) { tokens.add(token.toString()); token.setLength(0); }
            } else if (!quoted && c == '|') {
                if (token.length() > 0) { tokens.add(token.toString()); token.setLength(0); }
                tokens.add("OR");
            } else token.append(c);
        }
        if (quoted) return new SearchQuery(List.of(List.of()), "Missing closing quotation mark.");
        if (token.length() > 0) tokens.add(token.toString());
        List<List<Term>> groups = new ArrayList<>();
        List<Term> current = new ArrayList<>();
        for (String raw : tokens) {
            if (raw.equalsIgnoreCase("OR")) {
                if (current.isEmpty()) return new SearchQuery(List.of(List.of()), "OR needs a term on each side.");
                groups.add(current); current = new ArrayList<>(); continue;
            }
            boolean negative = raw.startsWith("-") && raw.length() > 1;
            String text = negative ? raw.substring(1) : raw;
            String field = "";
            int colon = text.indexOf(':');
            if (colon > 0 && FIELDS.contains(text.substring(0,colon).toLowerCase(Locale.ROOT))) {
                field = text.substring(0, colon).toLowerCase(Locale.ROOT);
                text = text.substring(colon + 1);
            }
            if (text.isBlank()) return new SearchQuery(List.of(List.of()), "Missing value for " + field + ".");
            current.add(new Term(field, text, negative));
        }
        if (current.isEmpty()) return new SearchQuery(List.of(List.of()), "OR needs a term on each side.");
        groups.add(current);
        return new SearchQuery(groups, null);
    }
}
