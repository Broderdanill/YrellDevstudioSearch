import java.util.List;
import java.util.Set;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.core.ReferenceGraph;
import se.yrell.search.bmc.FormExtractor;

public final class FormSearchTest {
    public static class FakeField {
        private final int id; private final String name;
        FakeField(int id, String name) { this.id = id; this.name = name; }
        public int getFieldID() { return id; }
        public String getName() { return name; }
        public String getDisplayLabel() { return "Label " + name; }
        public String getDataType() { return "CHAR"; }
        public String getMenuName() { return "Menu:" + name; }
    }
    public static class FakeForm {
        public List<FakeField> getFields() { return List.of(new FakeField(7,"Status"),
                new FakeField(17,"Status 2"),new FakeField(536870913,"Short Description"),
                new FakeField(7,"Status")); }
        public String getFormType() { return "Regular"; }
    }
    public static class WrappedForm {
        public FakeForm getForm() { return new FakeForm(); }
    }
    public static class ApiServer {
        public List<FakeField> getListFieldObjects(String formName) {
            if (!formName.equals("HPD:Help Desk")) throw new AssertionError();
            return List.of(new FakeField(42,"Hidden Field"));
        }
    }
    public static class Provider {
        public ApiServer getARServerUser() { return new ApiServer(); }
    }
    static void ok(boolean check, String msg) { if (!check) throw new AssertionError(msg); }
    public static void main(String[] args) {
        var metadata=FormExtractor.metadata("HPD:Help Desk",new FakeForm(),null);
        ok(metadata.complete() && metadata.fieldCount()==3,"unique fields / complete metadata");
        var form=new SearchIndex.Entry<>("Form","HPD:Help Desk","FORM",metadata.occurrences());
        var workflow=new SearchIndex.Entry<>("Filter","HPD:Push","FLOW",List.of(
                new SearchIndex.Occurrence("Target form","If Action 1","HPD:Help Desk",SearchIndex.Confidence.EXACT),
                new SearchIndex.Occurrence("Field reference","If Action 1","7",SearchIndex.Confidence.EXACT)));
        var index=new SearchIndex<>("s","u","",List.of(form,workflow),true,List.of());
        ok(index.search("fieldid:7",SearchIndex.Mode.ALL,Set.of("Form"),100).objects().size()==1,"field ID exact");
        ok(index.search("fieldid:17",SearchIndex.Mode.ALL,Set.of("Form"),100).objects().size()==1,"field ID 17");
        ok(index.search("fieldid:1",SearchIndex.Mode.ALL,Set.of("Form"),100).objects().isEmpty(),"no substring numeric ID");
        ok(index.search("fieldname:\"Short Description\"",SearchIndex.Mode.ALL,100).objects().size()==1,"field name");
        ok(index.search("form:\"HPD:Help Desk\" fieldid:536870913",SearchIndex.Mode.ALL,100).objects().size()==1,"form AND field");
        ok(index.search("label:\"Label Status\"",SearchIndex.Mode.ALL,100).objects().size()==1,"field label");
        ok(index.search("datatype:CHAR",SearchIndex.Mode.FIELDS,100).objects().size()==1,"data type");
        ok(index.search("menu:\"Menu:Status\"",SearchIndex.Mode.ALL,100).objects().size()==1,"field menu");
        ok(index.search("type:Form",SearchIndex.Mode.ALL,100).objects().size()==1,"form object type");
        ok(index.search("Short Description",SearchIndex.Mode.FIELDS,100).objects().size()==1,"plain field name");
        ok(index.search("fieldid:536870913",SearchIndex.Mode.FORMS,100).objects().isEmpty(),"form mode excludes fields");
        ok(index.search("form:\"HPD:Help Desk\"",SearchIndex.Mode.FORMS,100).objects().size()==2,"form name and reference");
        ok(index.search("fieldname:Status",SearchIndex.Mode.ALL,Set.of("Filter"),100).objects().isEmpty(),"type check excludes forms");
        ok(index.search("fieldid:7",SearchIndex.Mode.ALL,Set.of("Form"),1).objects().size()==1,"field result exports unique form");
        var graph=new ReferenceGraph<>(index);
        ok(graph.outgoing(workflow).stream().anyMatch(link->link.to()==form),"workflow resolves form");
        ok(graph.incoming(form).size()==1,"form receives incoming reference");
        var serverMetadata=FormExtractor.metadata("HPD:Help Desk",new FakeForm(),new Provider());
        ok(serverMetadata.fieldCount()==1 && serverMetadata.occurrences().stream().anyMatch(
                o->o.category().equals("Field name") && o.value().equals("Hidden Field")),"server API includes hidden fields");
        var wrapped=FormExtractor.metadata("HPD:Help Desk",new WrappedForm(),null);
        ok(wrapped.complete() && wrapped.fieldCount()==3,"form wrapper adapter");
        var unavailable=FormExtractor.metadata("Unavailable",new Object(),new Object());
        ok(!unavailable.complete() && !unavailable.problem().isBlank(),"unavailable field catalogue is partial");
        System.out.println("FormSearchTest: 19 PASS");
    }
}
