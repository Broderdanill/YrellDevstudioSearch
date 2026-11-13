import java.util.List;
import se.yrell.search.core.ObjectNames;

public final class ObjectNamesTest {
  private static int passed;
  private static void check(boolean ok, String what) { if(!ok) throw new AssertionError(what); passed++; }
  public static void main(String[] args) {
    check(ObjectNames.lines(List.of("AL:A", "FL:B", "AL:A")).equals("AL:A" + System.lineSeparator() + "FL:B"),"deduplicated lines");
    check(ObjectNames.uniqueCount(List.of("AL:A", "FL:B", "AL:A"))==2,"unique count");
    check(ObjectNames.lines(List.of("  ","FL:B")).equals("FL:B"),"ignore whitespace names");
    check(ObjectNames.lines(List.of()).isEmpty(),"empty selection");
    check(ObjectNames.lines(List.of("FL:B","AL:A")).startsWith("FL:B"),"selection order");
    System.out.println("ObjectNamesTest: " + passed + " PASS");
  }
}
