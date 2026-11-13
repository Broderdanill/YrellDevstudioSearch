package com.bmc.arsys.studio.model.store;
import java.util.Set;
import com.bmc.arsys.studio.model.type.IModelType;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
public interface IStore {
    boolean isConnected();
    String getName();
    String getUser();
    Set<IModelType> getSupportedTypes();
    ItemList<IModelItem> getList(IModelType type, boolean b);
    IModelObject getObject(IModelType type, String name);
    Object getProperty(String name);
}
