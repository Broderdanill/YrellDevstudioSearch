package com.bmc.arsys.studio.model.type;
public interface IARSystemTypes {
    record Type(String getTypeName) implements IModelType {}
    IModelType FORM = new Type("Form");
    IModelType FILTER = new Type("Filter");
    IModelType ESCALATION = new Type("Escalation");
    IModelType ACTIVE_LINK = new Type("Active Link");
    IModelType ACTIVE_LINK_GUIDE = new Type("Active Link Guide");
    IModelType FILTER_GUIDE = new Type("Filter Guide");
}
