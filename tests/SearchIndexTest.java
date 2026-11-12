import java.util.List;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.core.SearchIndex.*;

public final class SearchIndexTest {
    public static void main(String[] args) {
        var sender = new Entry<>("Active Link", "Send", "sender", List.of(
                new Occurrence("Name", "Object", "Send", Confidence.EXACT),
                new Occurrence("Event sender", "If Action 1", "ChildClosed | PERFORM-ACTION-SEND-EVENT @ ChildClosed", Confidence.POSSIBLE),
                new Occurrence("Run Process", "If Action 1", "PERFORM-ACTION-SEND-EVENT @ ChildClosed", Confidence.EXACT)));
        var receiver = new Entry<>("Active Link", "Receive", "receiver", List.of(
                new Occurrence("Event receiver", "Run If", "$EVENTTYPE$ = \"ChildClosed\"", Confidence.POSSIBLE),
                new Occurrence("Qualification", "Run If", "$EVENTTYPE$ = \"ChildClosed\"", Confidence.TEXT)));
        var index = new SearchIndex<>("test", "admin", "", List.of(sender, receiver), true, List.of());
        var all = index.search("ChildClosed", Mode.ALL, 50);
        ok(all.totalHits() == 4, "all hits");
        ok(all.objects().size() == 2, "unique export objects");
        var events = index.search("ChildClosed", Mode.EVENTS, 50);
        ok(events.totalHits() == 2, "event mode");
        var actions = index.search("ChildClosed", Mode.ACTIONS, 50);
        ok(actions.totalHits() == 1, "action mode");
        var limited = index.search("ChildClosed", Mode.ALL, 1);
        ok(limited.truncated() && limited.hits().size() == 1 && limited.totalHits() == 4, "result limit");
        ok(index.search("nope", Mode.ALL, 50).totalHits() == 0, "no match");
        ok(index.search("", Mode.ALL, 50).hits().isEmpty(), "empty query");
        ok(index.search("childclosed", Mode.EVENTS, 50).totalHits() == 2, "case insensitive");
        System.out.println("SearchIndexTest: 8 PASS");
    }
    static void ok(boolean b, String message) { if (!b) throw new AssertionError(message); }
}
