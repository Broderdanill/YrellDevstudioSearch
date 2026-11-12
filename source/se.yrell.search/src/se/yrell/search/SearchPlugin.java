package se.yrell.search;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public final class SearchPlugin extends AbstractUIPlugin {
    public static final String ID = "se.yrell.search";
    private static volatile SearchPlugin instance;
    @Override public void start(BundleContext ctx) throws Exception { super.start(ctx); instance = this; }
    @Override public void stop(BundleContext ctx) throws Exception { instance = null; super.stop(ctx); }
    public static void error(String message, Throwable t) {
        SearchPlugin p = instance;
        if (p != null) p.getLog().log(new Status(IStatus.ERROR, ID, message, t));
        else System.err.println(ID + ": " + message + " - " + t);
    }
}
