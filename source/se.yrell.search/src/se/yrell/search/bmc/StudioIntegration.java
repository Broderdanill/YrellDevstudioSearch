package se.yrell.search.bmc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import com.bmc.arsys.apiext.definition.DefinitionOptions;
import com.bmc.arsys.apiext.definition.RelatedType;
import com.bmc.arsys.studio.model.item.IModelItem;
import com.bmc.arsys.studio.model.item.ItemList;
import com.bmc.arsys.studio.model.store.IARServerStore;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IARSystemTypes;
import com.bmc.arsys.studio.ui.common.definition.DefinitionHelper;
import com.bmc.arsys.studio.ui.views.objectlist.actions.OpenDefaultEditorAction;
import com.bmc.arsys.studio.ui.views.objectlist.actions.definition.ExportObjectDialog;

/** Reuses BMC's editor and DEF export path. Must execute on SWT UI thread. */
public final class StudioIntegration {
    private StudioIntegration() {}
    public static void open(Shell shell, List<IModelItem> items) {
        if (items.isEmpty()) return;
        ArrayList<IModelItem> unique = new ArrayList<>(unique(items));
        OpenDefaultEditorAction action = new OpenDefaultEditorAction();
        action.setItems(unique);
        try { action.run(); }
        catch (RuntimeException ex) { MessageDialog.openError(shell, "Yrell Search", "Could not open selected object: " + ex.getMessage()); }
    }
    public static void export(Shell shell, List<IModelItem> items) {
        List<IModelItem> unique = unique(items);
        if (unique.isEmpty()) return;
        IStore store = unique.get(0).getStore();
        if (!(store instanceof IARServerStore ar) || !store.isConnected()) {
            MessageDialog.openError(shell, "Yrell Search", "The source AR server is no longer connected."); return;
        }
        for (IModelItem item : unique) {
            if (item.getStore() != store) {
                MessageDialog.openError(shell, "Yrell Search", "Export cannot mix objects from different server connections."); return;
            }
        }
        ItemList<IModelItem> objects = new ItemList<>();
        for (IModelItem item : unique) objects.addItem(item);
        boolean formsOnly = objects.getItemTypes().size() == 1 && objects.getItemTypes().contains(IARSystemTypes.FORM);
        ExportObjectDialog dialog = new ExportObjectDialog(shell, objects, formsOnly);
        if (dialog.open() != Window.OK) return;
        if (dialog.getFileName() == null || dialog.getFileName().isBlank()) return;
        try {
            RelatedType related = dialog.isExportRelated() ? RelatedType.DIRECTLY : RelatedType.NONE;
            DefinitionOptions options = new DefinitionOptions();
            options.setOverlayGroup(ar.getOverlayGroup());
            options.setUser(ar.getUser());
            options.setPassword(ar.getPassword()); // BMC API needs credentials; never log or persist them.
            options.setServer(ar.getName());
            options.setAuthentication(ar.getAuthentication());
            options.setFileName(dialog.getFileName());
            options.setServerIndependent(dialog.isExportServerIndependent());
            options.setOverwrite(dialog.isOverwrite());
            options.addAll(DefinitionHelper.convertItemsForExport(objects, related));
            DefinitionHelper.exportObjects(store, options);
        } catch (RuntimeException ex) {
            MessageDialog.openError(shell, "Yrell Search", "BMC export could not be started: " + ex.getMessage());
        }
    }
    private static List<IModelItem> unique(List<IModelItem> objects) {
        Map<String, IModelItem> map = new LinkedHashMap<>();
        for (IModelItem item : objects) {
            if (item == null) continue;
            String key = System.identityHashCode(item.getStore()) + "\u0000"
                    + item.getItemType().getTypeName() + "\u0000" + item.getName();
            map.putIfAbsent(key, item);
        }
        return List.copyOf(map.values());
    }
}
