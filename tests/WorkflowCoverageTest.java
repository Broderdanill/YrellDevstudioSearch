import java.lang.reflect.Proxy;
import java.util.List;
import com.bmc.arsys.api.*;
import com.bmc.arsys.studio.model.ar.ARActiveLink;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.bmc.WorkflowExtractor;

public final class WorkflowCoverageTest {
  public static void main(String[] args) {
    IModelItem item=(IModelItem)Proxy.newProxyInstance(IModelItem.class.getClassLoader(),new Class[]{IModelItem.class},
        (proxy,method,args1)->switch(method.getName()) { case "getName" -> "Testing";
          case "getItemType" -> IARSystemTypes.ACTIVE_LINK; default -> null; });
    ARActiveLink al=new ARActiveLink();
    al.setFormList(List.of("My:Form"));
    al.setExecuteMask(Constants.AR_EXECUTE_ON_EVENT);
    SetFieldsFromCurrentScreen set=new SetFieldsFromCurrentScreen();
    FieldAssignInfo assignment=new FieldAssignInfo(); assignment.setFieldId(536870913);
    set.setSetFieldsList(List.of(assignment));
    PushFieldsAction push=new PushFieldsAction(); push.setPushToForm("Other:Target");
    CallGuideAction guide=new CallGuideAction(); guide.setGuideName("My:Guide");
    OpenWindowAction window=new OpenWindowAction(); window.setFormName("My:OtherForm");
    MessageAction message=new MessageAction(); message.setMessageText("Helpful warning");
    al.setActionList(List.of(new RunProcessAction("PERFORM-ACTION-SEND-EVENT @ \"Event With Spaces\""),set,push,guide,window,message));
    al.setElseList(List.of(new RunProcessAction("PERFORM-ACTION-SEND-EVENT @ OtherEvent")));
    var e=WorkflowExtractor.extract(item,al);
    var idx=new SearchIndex<>("s","u","",List.of(e),true,List.of());
    ok(idx.search("event:\"Event With Spaces\"", SearchIndex.Mode.ALL,30).objects().size()==1,"quoted event name");
    ok(idx.search("event:OtherEvent",SearchIndex.Mode.ALL,30).hits().stream().anyMatch(h->h.occurrence().location().contains("Else")),"else event");
    ok(idx.search("field:536870913",SearchIndex.Mode.ALL,30).objects().size()==1,"mapping target field id");
    ok(idx.search("form:Other:Target",SearchIndex.Mode.ALL,30).objects().size()==1,"push target form");
    ok(idx.search("guide:My:Guide",SearchIndex.Mode.ALL,30).objects().size()==1,"call guide");
    ok(idx.search("form:My:OtherForm",SearchIndex.Mode.ALL,30).objects().size()==1,"open window form");
    ok(idx.search("Helpful warning",SearchIndex.Mode.ALL,30).objects().size()==1,"message content");
    System.out.println("WorkflowCoverageTest: 7 PASS");
  }
  static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
}
