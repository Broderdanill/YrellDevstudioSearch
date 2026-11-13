package com.bmc.arsys.studio.model.item;
import com.bmc.arsys.api.Timestamp;
import com.bmc.arsys.studio.model.type.IModelType;
public interface IModelItem {
    String getName();
    IModelType getItemType();
    Timestamp getLastUpdateTime();
    void update(com.bmc.arsys.studio.model.store.IModelObject object);
}
