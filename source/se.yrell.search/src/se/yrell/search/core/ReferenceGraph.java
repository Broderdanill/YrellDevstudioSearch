package se.yrell.search.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservative, inspectable connections. No edge implies runtime execution. */
public final class ReferenceGraph<T> {
    public record Link<T>(SearchIndex.Entry<T> from, SearchIndex.Entry<T> to,
                          String relation, String location, String targetName,
                          SearchIndex.Confidence confidence) {}
    private final Map<SearchIndex.Entry<T>,List<Link<T>>> outgoing = new HashMap<>();
    private final Map<SearchIndex.Entry<T>,List<Link<T>>> incoming = new HashMap<>();
    private final List<Link<T>> all = new ArrayList<>();
    private static final int MAX_EDGES_PER_OBJECT = 400;

    public ReferenceGraph(SearchIndex<T> index) {
        Map<String,List<SearchIndex.Entry<T>>> byName = new HashMap<>();
        Map<String,List<SearchIndex.Entry<T>>> senders = new HashMap<>();
        Map<String,List<SearchIndex.Entry<T>>> receivers = new HashMap<>();
        for (SearchIndex.Entry<T> e : index.entries()) {
            byName.computeIfAbsent(normal(e.name()), k -> new ArrayList<>()).add(e);
            for (SearchIndex.Occurrence o : e.occurrences()) {
                if (o.category().equals("Event sender") && !isDynamic(o.value()))
                    senders.computeIfAbsent(normal(o.value()), k -> new ArrayList<>()).add(e);
                if (o.category().equals("Event receiver") && !isDynamic(o.value()) && !o.value().startsWith("On Event"))
                    receivers.computeIfAbsent(normal(o.value()), k -> new ArrayList<>()).add(e);
            }
        }
        for (SearchIndex.Entry<T> e : index.entries()) {
            Set<String> dedupe = new HashSet<>();
            for (SearchIndex.Occurrence o : e.occurrences()) {
                if (outgoing.getOrDefault(e, List.of()).size() >= MAX_EDGES_PER_OBJECT) break;
                String cat = o.category();
                String value = o.value();
                if (value == null || value.isBlank()) continue;
                if (cat.equals("Form") || cat.equals("Target form") || cat.equals("Service form")) {
                    // Forms aren't indexed as search objects; retain an explicit unresolved reference.
                    attach(e, null, cat.equals("Form") ? "Associated form" : "References form",
                            o.location(), value, o.confidence(), dedupe);
                } else if (cat.equals("Guide reference") || cat.equals("Guide call")) {
                    List<SearchIndex.Entry<T>> targets = byName.getOrDefault(normal(value), List.of());
                    String wanted = cat.equals("Guide call") ? "Guide" : e.type().equalsIgnoreCase("Filter Guide") ? "Filter" : "Active Link";
                    boolean resolved = false;
                    for (SearchIndex.Entry<T> t : targets) {
                        if (!t.type().toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT))) continue;
                        attach(e,t,cat.equals("Guide call") ? "Calls guide" : "Guide step",o.location(),value,o.confidence(),dedupe);
                        resolved = true;
                    }
                    if (!resolved) attach(e,null,cat.equals("Guide call") ? "Calls guide" : "Guide step",
                            o.location(),value,SearchIndex.Confidence.POSSIBLE,dedupe);
                } else if (cat.equals("Event sender")) {
                    List<SearchIndex.Entry<T>> targets = receivers.getOrDefault(normal(value), List.of());
                    if (targets.isEmpty()) attach(e,null,"Sends event",o.location(),value,o.confidence(),dedupe);
                    else for (SearchIndex.Entry<T> t : targets) {
                        if (t == e) continue;
                        attach(e,t,"Possible event delivery",o.location(),value,SearchIndex.Confidence.POSSIBLE,dedupe);
                    }
                }
            }
        }
    }
    private void attach(SearchIndex.Entry<T> from, SearchIndex.Entry<T> to, String kind, String where,
                        String name, SearchIndex.Confidence confidence, Set<String> dedupe) {
        String key = kind + '\0' + where + '\0' + name + '\0' + (to == null ? "" : to.type() + to.name());
        if (!dedupe.add(key)) return;
        Link<T> link = new Link<>(from,to,kind,where,name,confidence);
        outgoing.computeIfAbsent(from,k->new ArrayList<>()).add(link);
        if (to != null) incoming.computeIfAbsent(to,k->new ArrayList<>()).add(link);
        all.add(link);
    }
    public List<Link<T>> outgoing(SearchIndex.Entry<T> e) { return Collections.unmodifiableList(outgoing.getOrDefault(e, List.of())); }
    public List<Link<T>> incoming(SearchIndex.Entry<T> e) { return Collections.unmodifiableList(incoming.getOrDefault(e, List.of())); }
    public int totalLinks() { return all.size(); }
    private static String normal(String s) { return s.trim().toLowerCase(Locale.ROOT); }
    private static boolean isDynamic(String s) { return s.indexOf('$') >= 0 || s.indexOf('%') >= 0; }
}
