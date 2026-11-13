import java.lang.reflect.Proxy;
import java.util.List;
import com.bmc.arsys.api.*;
import com.bmc.arsys.studio.model.ar.ARActiveLink;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.bmc.WorkflowExtractor;

public final class QualifierDeepTest {
  public static void main(String[] args) {
    IModelItem item=(IModelItem)Proxy.newProxyInstance(IModelItem.class.getClassLoader(),new Class[]{IModelItem.class},
      (proxy,m,a)->switch(m.getName()) { case "getName" -> "Qualified"; case "getItemType" -> IARSystemTypes.ACTIVE_LINK; default -> null; });
    ARActiveLink al=new ARActiveLink();
    al.setQualifier(new QualifierInfo(new RelationalOperationInfo(1,
      new ArithmeticOrRelationalOperand(536870913),new ArithmeticOrRelationalOperand(new Value("ExpectedText")))));
    var e=WorkflowExtractor.extract(item,al);
    var idx=new SearchIndex<>("s","u","",List.of(e),true,List.of());
    boolean byId=idx.search("field:536870913", SearchIndex.Mode.ALL,50).totalHits()>0;
    boolean byText=idx.search("ExpectedText", SearchIndex.Mode.ALL,50).totalHits()>0;
    if(!byId || !byText) {
      System.err.println("Fields: "+e.occurrences());
      throw new AssertionError("Qualifier traversal failed id="+byId+" text="+byText);
    }
    System.out.println("QualifierDeepTest: 2 PASS");
  }
}
