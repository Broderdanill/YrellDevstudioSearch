import java.util.List;
import java.util.Set;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.core.SearchIndex.*;
import se.yrell.search.core.SearchQuery;

public final class SearchQueryTest {
    public static void main(String[] args) {
        ok(SearchQuery.parse("form:\"HPD:Help Desk\" action:PushFields").error()==null,"quoted scoped terms");
        ok(SearchQuery.parse("\"unfinished").error()!=null,"unclosed phrase rejected");
        ok(SearchQuery.parse("a OR").error()!=null,"dangling OR rejected");
        var a=new Entry<>("Filter","F1","f1",List.of(
            new Occurrence("Form","Associated form","HPD:Help Desk",Confidence.EXACT),
            new Occurrence("Action","If Action 2","PushFieldsAction",Confidence.EXACT),
            new Occurrence("Assignment","If Action 2 / Mapping 1","Status: Assigned",Confidence.TEXT)));
        var b=new Entry<>("Active Link","A1","a1",List.of(
            new Occurrence("Form","Associated form","AST:Other",Confidence.EXACT),
            new Occurrence("Action","Else Action 1","RunProcessAction",Confidence.EXACT)));
        var c=new Entry<>("Filter","F2","f2",List.of(
            new Occurrence("Form","Associated form","AST:Other",Confidence.EXACT),
            new Occurrence("Action","If Action 1","PushFieldsAction",Confidence.EXACT)));
        var idx=new SearchIndex<>("s","u","",List.of(a,b,c),true,List.of());
        ok(idx.search("form:\"HPD:Help Desk\" action:PushFields",Mode.ALL,100).objects().size()==1,"AND fields and quotes");
        ok(idx.search("action:PushFields -form:AST",Mode.ALL,100).objects().size()==1,"negative scopes");
        ok(idx.search("name:F1 OR name:A1",Mode.ALL,100).objects().size()==2,"OR groups");
        ok(idx.search("action:PushFields",Mode.ALL,Set.of("Active Link"),100).objects().isEmpty(),"object type filter");
        ok(idx.search("action:PushFields",Mode.ALL,Set.of("Filter"),100).objects().size()==2,"multi-type search");
        ok(idx.search("action:PushFields",Mode.ALL,1).objects().size()==2,"full unique object list despite display limit");
        ok(idx.search("action:PushFields",Mode.ALL,1).hits().size()==1,"display capped");
        ok(idx.search("field:Status",Mode.FIELDS,100).objects().size()==1,"field scope");
        ok(idx.search("where:Else",Mode.ALL,100).objects().size()==1,"action location");
        ok(idx.search("type:Filter",Mode.ALL,100).objects().size()==2,"type scoped query");
        ok(idx.search("name:F1",Mode.ALL,100).objects().size()==1,"name scoped query");
        System.out.println("SearchQueryTest: 13 PASS");
    }
    static void ok(boolean b,String m) { if(!b) throw new AssertionError(m); }
}
