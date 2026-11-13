import java.util.List;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.core.ReferenceGraph;
import se.yrell.search.core.SearchIndex.*;

public final class ReferenceGraphTest {
  public static void main(String[] args) {
    Entry<String> sender=new Entry<>("Active Link","Sender","a",List.of(new Occurrence("Event sender","If Action 1","ChildClosed",Confidence.EXACT)));
    Entry<String> recipient=new Entry<>("Active Link","Receiver","b",List.of(new Occurrence("Event receiver","Run If","ChildClosed",Confidence.POSSIBLE)));
    Entry<String> caller=new Entry<>("Active Link","Caller","c",List.of(new Occurrence("Guide call","If Action 2","SomeGuide",Confidence.EXACT)));
    Entry<String> guide=new Entry<>("Active Link Guide","SomeGuide","d",List.of(new Occurrence("Guide reference","Step 1","Caller",Confidence.EXACT)));
    Entry<String> filter=new Entry<>("Filter","AFilter","e",List.of(new Occurrence("Target form","If Action 2","HD:Incident",Confidence.EXACT)));
    var idx=new SearchIndex<>("s","u","",List.of(sender,recipient,caller,guide,filter),true,List.of());
    var g=new ReferenceGraph<>(idx);
    ok(g.outgoing(sender).stream().anyMatch(l->l.to()==recipient && l.confidence()==Confidence.POSSIBLE),"possible event route");
    ok(g.incoming(recipient).size()==1,"reverse route");
    ok(g.outgoing(caller).stream().anyMatch(l->l.to()==guide),"call guide resolution");
    ok(g.outgoing(guide).stream().anyMatch(l->l.to()==caller),"guide reference resolution");
    ok(g.outgoing(filter).stream().anyMatch(l->l.to()==null && l.targetName().equals("HD:Incident")),"unresolved form is displayed");
    ok(g.totalLinks()>=4,"all links counted");
    System.out.println("ReferenceGraphTest: 6 PASS");
  }
  static void ok(boolean b,String m){ if(!b)throw new AssertionError(m); }
}
