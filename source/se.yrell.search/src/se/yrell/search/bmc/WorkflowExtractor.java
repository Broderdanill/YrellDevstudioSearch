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

/** Reads saved BMC definitions, preserves If/Else positions; no mutating AR API calls. */
public final class WorkflowExtractor {
    // A quoted event can contain spaces. Unquoted @ syntax and bare token are also accepted.
    private static final Pattern SEND = Pattern.compile("(?i)\\bPERFORM-ACTION-SEND-EVENT\\b\\s*(?:@\\s*)?(?:\\\"([^\\\"]+)\\\"|'([^']+)'|([^\\s\\\"']+))");
    private static final Pattern EVENT_TEST = Pattern.compile("(?i)(?:\\$EVENTTYPE\\$|EVENTTYPE)\\s*(?:=|==|LIKE)\\s*[\\\"']([^\\\"']+)[\\\"']");
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
            add(facts, "Enabled", "Active Link", String.valueOf(al.isEnable()), Confidence.EXACT);
            add(facts, "Execution order", "Active Link", String.valueOf(al.getOrder()), Confidence.EXACT);
            add(facts, "Execute on", "Active Link", String.valueOf(al.getExecuteMask()), Confidence.EXACT);
            try { add(facts, "Execute on", "Active Link", String.join(", ",
                    com.bmc.arsys.studio.model.item.ActiveLinkItem.decodeExecuteOption(al.getExecuteMask())), Confidence.EXACT); }
            catch (RuntimeException ignored) { /* preserve numeric mask */ }
            if (al.getControlField() > 0) add(facts, "Field reference", "Control Field ID", String.valueOf(al.getControlField()), Confidence.EXACT);
            if (al.getFocusField() > 0) add(facts, "Field reference", "Focus Field ID", String.valueOf(al.getFocusField()), Confidence.EXACT);
            qualifier(facts, al.getQualifier(), "Run If");
            if ((al.getExecuteMask() & Constants.AR_EXECUTE_ON_EVENT) != 0) {
                String expression = safe(al.getQualifier());
                Matcher m = EVENT_TEST.matcher(expression);
                boolean found = false;
                while (m.find()) { found = true; add(facts, "Event receiver", "Run If ($EVENTTYPE$)", m.group(1), Confidence.POSSIBLE); }
                if (!found) add(facts, "Event receiver", "Execute On: Event", "On Event (name may be dynamic or unrestricted)", Confidence.POSSIBLE);
            }
            actions(facts, al.getActionList(), "If"); actions(facts, al.getElseList(), "Else");
        } else if (definition instanceof Filter f) {
            forms(facts, f.getFormList());
            add(facts, "Enabled", "Filter", String.valueOf(f.isEnable()), Confidence.EXACT);
            add(facts, "Execution order", "Filter", String.valueOf(f.getOrder()), Confidence.EXACT);
            add(facts, "Execute on", "Filter", String.valueOf(f.getOpSet()), Confidence.EXACT);
            qualifier(facts, f.getQualifier(), "Run If");
            actions(facts, f.getActionList(), "If"); actions(facts, f.getElseList(), "Else");
        } else if (definition instanceof Escalation e) {
            forms(facts, e.getFormList());
            add(facts, "Enabled", "Escalation", String.valueOf(e.isEnable()), Confidence.EXACT);
            add(facts, "Schedule", "Escalation", safe(e.getEscalationTm()), Confidence.TEXT);
            ActionPropertyWalker.inspect(e.getEscalationTm(), "Schedule", facts);
            qualifier(facts, e.getQualifier(), "Run If");
            actions(facts, e.getActionList(), "If"); actions(facts, e.getElseList(), "Else");
        } else if (definition instanceof ARActiveLinkGuide g) {
            forms(facts, g.getFormList()); references(facts, g.getWorkflowReferences());
        } else if (definition instanceof ARFilterGuide g) {
            forms(facts, g.getFormList()); references(facts, g.getWorkflowReferences());
        }
        return new SearchIndex.Entry<>(type, name, item, facts);
    }
    private static void references(List<Occurrence> facts, List<Reference> refs) {
        if (refs == null) return;
        int i = 0;
        for (Reference ref : refs) {
            i++;
            if (ref == null) continue;
            add(facts, "Guide reference", "Guide step " + i, ref.getName(), Confidence.EXACT);
            add(facts, "Guide label", "Guide step " + i, ref.getLabel(), Confidence.TEXT);
        }
    }
    private static void forms(List<Occurrence> facts, List<String> forms) {
        if (forms == null) return;
        for (String form : forms) add(facts, "Form", "Associated form", form, Confidence.EXACT);
    }
    private static void qualifier(List<Occurrence> facts, QualifierInfo q, String where) {
        if (q == null) return;
        add(facts, "Qualification", where, safe(q), Confidence.TEXT);
        ActionPropertyWalker.inspect(q, where, facts);
    }
    private static void actions(List<Occurrence> facts, List<?> actions, String branch) {
        if (actions == null) return;
        for (int i = 0; i < actions.size(); i++) {
            Object action = actions.get(i);
            if (action == null) continue;
            String path = branch + " Action " + (i + 1);
            String kind = action.getClass().getSimpleName();
            add(facts, "Action", path, kind, Confidence.EXACT);
            if (action instanceof RunProcessAction run) {
                String cmd = run.getCommandLine();
                add(facts, "Run Process", path, cmd, Confidence.EXACT);
                if (cmd != null) {
                    Matcher m = SEND.matcher(cmd);
                    while (m.find()) {
                        String event = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
                        add(facts, "Event sender", path, event, event.contains("$") ? Confidence.POSSIBLE : Confidence.EXACT);
                    }
                }
            }
            if (action instanceof PushFieldsAction push) {
                add(facts, "Target form", path, push.getPushToForm(), Confidence.EXACT);
                add(facts, "Target server", path, push.getPushToServer(), Confidence.TEXT);
                qualifier(facts, push.getPushIfQualification(), path + " / Push If");
                int n = 0;
                for (PushFieldsInfo field : nullSafe(push.getPushFieldsList())) {
                    n++;
                    String loc = path + " / Push mapping " + n;
                    if (field.getField() != null) {
                        add(facts, "Field reference", loc + " / Target field ID", String.valueOf(field.getField().getFieldId()), Confidence.EXACT);
                        add(facts, "Target form", loc + " / Source", field.getField().getForm(), Confidence.TEXT);
                    }
                    add(facts, "Assignment", loc, field.getAssignmentAsString(), Confidence.TEXT);
                    ActionPropertyWalker.inspect(field, loc, facts);
                }
            }
            if (action instanceof SetFieldsAction set) {
                add(facts, "Target form", path + " / Read form", set.getReadValuesFrom(), Confidence.TEXT);
                add(facts, "Target form", path + " / Sample form", set.getSampleForm(), Confidence.TEXT);
                int n = 0;
                for (FieldAssignInfo field : nullSafe(set.getSetFieldsList())) {
                    n++;
                    String loc = path + " / Set mapping " + n;
                    add(facts, "Field reference", loc + " / Target field ID", String.valueOf(field.getFieldId()), Confidence.EXACT);
                    add(facts, "Assignment", loc, field.getAssignmentAsString(), Confidence.TEXT);
                    ActionPropertyWalker.inspect(field, loc, facts);
                }
            }
            if (action instanceof OpenWindowAction win) {
                add(facts, "Target form", path, win.getFormName(), Confidence.EXACT);
                add(facts, "Target view", path, win.getVuiLabel(), Confidence.EXACT);
                qualifier(facts, win.getQuery(), path + " / Query");
            }
            if (action instanceof CallGuideAction call) {
                add(facts, "Guide call", path, call.getGuideName(), Confidence.EXACT);
                add(facts, "Target server", path, call.getServerName(), Confidence.TEXT);
            }
            if (action instanceof ServiceAction service) {
                add(facts, "Service form", path, service.getServiceForm(), Confidence.EXACT);
            }
            if (action instanceof ChangeFieldAction change) {
                add(facts, "Field reference", path + " / Field ID", String.valueOf(change.getFieldId()), Confidence.EXACT);
                add(facts, "Menu reference", path, change.getCharMenu(), Confidence.TEXT);
            }
            if (action instanceof MessageAction msg) {
                add(facts, "Message", path, msg.getMessageText(), Confidence.TEXT);
                add(facts, "Message number", path, String.valueOf(msg.getMessageNum()), Confidence.EXACT);
            }
            if (action instanceof NotifyAction notify) {
                add(facts, "Notification", path + " / Subject", notify.getSubjectText(), Confidence.TEXT);
                add(facts, "Notification", path + " / Body", notify.getNotifyText(), Confidence.TEXT);
                add(facts, "Notification", path + " / Recipient", notify.getUser(), Confidence.TEXT);
            }
            // All other BMC action value object properties are searchable through bounded traversal.
            ActionPropertyWalker.inspect(action, path, facts);
        }
    }
    private static <T> List<T> nullSafe(List<T> values) { return values == null ? List.of() : values; }
    private static String safe(Object o) { try { return o == null ? "" : String.valueOf(o); } catch (RuntimeException ex) { return ""; } }
    private static void add(List<Occurrence> facts, String category, String where, String value, Confidence certainty) {
        if (value == null || value.isBlank() || facts.size() >= 2000) return;
        if (value.length() > 16_000) value = value.substring(0, 16_000) + " [TRUNCATED]";
        facts.add(new Occurrence(category, where, value, certainty));
    }
}
