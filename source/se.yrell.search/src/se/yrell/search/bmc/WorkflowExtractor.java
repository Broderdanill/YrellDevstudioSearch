package se.yrell.search.bmc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.bmc.arsys.api.*;
import com.bmc.arsys.studio.model.ar.ARActiveLinkGuide;
import com.bmc.arsys.studio.model.ar.ARFilterGuide;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.store.IModelObject;
import se.yrell.search.core.SearchIndex;
import se.yrell.search.core.SearchIndex.Confidence;
import se.yrell.search.core.SearchIndex.Occurrence;

/** Extracts searchable facts, never mutates BMC objects or calls write APIs. */
public final class WorkflowExtractor {
    private static final Pattern SEND = Pattern.compile("(?i)PERFORM-ACTION-SEND-EVENT\\s+@?\\s*([^\\s]+)");
    private WorkflowExtractor() {}
    public static SearchIndex.Entry<IModelItem> extract(IModelItem item, IModelObject definition) {
        List<Occurrence> facts = new ArrayList<>();
        String type = item.getItemType().getTypeName();
        String name = item.getName();
        add(facts, "Name", "Object", name, Confidence.EXACT);
        add(facts, "Type", "Object", type, Confidence.EXACT);
        if (definition instanceof ObjectBase base) {
            add(facts, "Description", "Object", base.getHelpText(), Confidence.TEXT);
        }
        if (definition instanceof ActiveLink al) {
            forms(facts, al.getFormList());
            add(facts, "Execute on", "Active Link", String.valueOf(al.getExecuteMask()), Confidence.EXACT);
            try { add(facts, "Execute on", "Active Link", String.join(", ",
                    com.bmc.arsys.studio.model.item.ActiveLinkItem.decodeExecuteOption(al.getExecuteMask())), Confidence.EXACT); }
            catch (RuntimeException ignored) { /* mask retained */ }
            if ((al.getExecuteMask() & Constants.AR_EXECUTE_ON_EVENT) != 0) {
                // On Event is NOT proof that this AL receives a particular named event.
                add(facts, "Event receiver", "Execute On: Event", "On Event: possible receiver; inspect Run If", Confidence.POSSIBLE);
                add(facts, "Event receiver", "Run If", safe(al.getQualifier()), Confidence.POSSIBLE);
            }
            qualifier(facts, al.getQualifier());
            actions(facts, al.getActionList(), "If");
            actions(facts, al.getElseList(), "Else");
        } else if (definition instanceof Filter f) {
            forms(facts, f.getFormList());
            add(facts, "Execute on", "Filter", String.valueOf(f.getOpSet()), Confidence.EXACT);
            qualifier(facts, f.getQualifier());
            actions(facts, f.getActionList(), "If");
            actions(facts, f.getElseList(), "Else");
        } else if (definition instanceof Escalation e) {
            forms(facts, e.getFormList());
            qualifier(facts, e.getQualifier());
            actions(facts, e.getActionList(), "If");
            actions(facts, e.getElseList(), "Else");
        } else if (definition instanceof ARActiveLinkGuide g) {
            forms(facts, g.getFormList());
            references(facts, g.getWorkflowReferences());
        } else if (definition instanceof ARFilterGuide g) {
            forms(facts, g.getFormList());
            references(facts, g.getWorkflowReferences());
        }
        return new SearchIndex.Entry<>(type, name, item, facts);
    }
    private static void references(List<Occurrence> facts, List<Reference> refs) {
        if (refs == null) return;
        int i = 0;
        for (Reference ref : refs) {
            if (ref != null) add(facts, "Guide reference", "Reference " + (++i),
                    ref.getName(), Confidence.EXACT);
        }
    }
    private static void forms(List<Occurrence> facts, List<String> forms) {
        if (forms == null) return;
        for (String form : forms) add(facts, "Form", "Associated form", form, Confidence.EXACT);
    }
    private static void qualifier(List<Occurrence> facts, QualifierInfo q) {
        if (q != null) add(facts, "Qualification", "Run If", safe(q), Confidence.TEXT);
    }
    private static void actions(List<Occurrence> facts, List<?> actions, String branch) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            Object action = actions.get(i);
            if (action == null) continue;
            String path = branch + " Action " + (i + 1);
            String kind = action.getClass().getSimpleName();
            add(facts, "Action", path, kind, Confidence.EXACT);
            // Keep a broad text fallback for action properties that lack a dedicated adapter.
            add(facts, "Action", path + " details", safe(action), Confidence.TEXT);
            if (action instanceof RunProcessAction run) {
                String cmd = run.getCommandLine();
                add(facts, "Run Process", path, cmd, Confidence.EXACT);
                if (cmd != null) {
                    Matcher m = SEND.matcher(cmd);
                    if (m.find()) add(facts, "Event sender", path, m.group(1) + " | " + cmd,
                            Confidence.POSSIBLE); // syntax can include dynamic values; cannot prove dispatch
                }
            }
            if (action instanceof PushFieldsAction push) {
                add(facts, "Target form", path, push.getPushToForm(), Confidence.EXACT);
                add(facts, "Target form", path + " sample", push.getSampleForm(), Confidence.TEXT);
                if (push.getPushIfQualification() != null)
                    add(facts, "Qualification", path + " Push If", safe(push.getPushIfQualification()), Confidence.TEXT);
                for (PushFieldsInfo field : nullSafe(push.getPushFieldsList()))
                    add(facts, "Assignment", path + " Push mapping", safe(field), Confidence.TEXT);
            }
            if (action instanceof SetFieldsAction set) {
                add(facts, "Target form", path + " read form", set.getSampleForm(), Confidence.TEXT);
                for (FieldAssignInfo field : nullSafe(set.getSetFieldsList())) {
                    add(facts, "Field reference", path + " target Field ID", String.valueOf(field.getFieldId()), Confidence.EXACT);
                    add(facts, "Assignment", path + " Set mapping", safe(field.getAssignment()), Confidence.TEXT);
                    add(facts, "Assignment", path + " Set mapping text", field.getAssignmentAsString(), Confidence.TEXT);
                }
            }
            if (action instanceof OpenWindowAction win) {
                add(facts, "Target form", path, win.getFormName(), Confidence.EXACT);
                if (win.getQuery() != null) add(facts, "Qualification", path + " Query", safe(win.getQuery()), Confidence.TEXT);
            }
            if (action instanceof ChangeFieldAction change)
                add(facts, "Field reference", path + " Field ID", String.valueOf(change.getFieldId()), Confidence.EXACT);
        }
    }
    private static <T> List<T> nullSafe(List<T> values) { return values == null ? List.of() : values; }
    private static String safe(Object o) { try { return o == null ? "" : String.valueOf(o); } catch (RuntimeException ex) { return ""; } }
    private static void add(List<Occurrence> facts, String category, String where, String value, Confidence certainty) {
        if (value == null || value.isBlank()) return;
        // Bound memory even for unusually large workflow literals.
        if (value.length() > 24_000) value = value.substring(0, 24_000) + " [TRUNCATED]";
        facts.add(new Occurrence(category, where, value, certainty));
    }
}
