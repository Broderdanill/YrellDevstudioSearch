package se.yrell.search.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

/** Compact read-only relationship map. A table below remains the authoritative, interactive list. */
public final class ConnectionOverview extends Canvas {
    private String center = "Select an object";
    private List<String> inbound = List.of();
    private List<String> outbound = List.of();

    public ConnectionOverview(Composite parent) {
        super(parent, SWT.DOUBLE_BUFFERED | SWT.BORDER);
        addPaintListener(this::draw);
        setToolTipText("Relationship overview. Use the table below for exact links and opening objects.");
    }
    public void showObject(String name, List<String> incoming, List<String> outgoing) {
        center=name==null?"Select an object":name;
        inbound=unique(incoming);
        outbound=unique(outgoing);
        redraw();
    }
    private static List<String> unique(List<String> input) {
        LinkedHashSet<String> distinct=new LinkedHashSet<>(input);
        return new ArrayList<>(distinct);
    }
    private void draw(PaintEvent e) {
        GC gc=e.gc;
        Rectangle bounds=getClientArea();
        int w=bounds.width, h=bounds.height;
        Color fg=getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
        Color muted=getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
        Color fill=getDisplay().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
        Color bg=getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        gc.setBackground(bg); gc.fillRectangle(bounds);
        if(w<270 || h<95) {
            gc.setForeground(fg); gc.drawText("Connections: " + inbound.size() + " incoming · " + outbound.size() + " outgoing",8,8,true);
            return;
        }
        int gap=14, centerWidth=Math.min(210,Math.max(125,w/3));
        int leftWidth=Math.max(76,(w-centerWidth)/2-2*gap);
        int rightWidth=leftWidth;
        int centerX=(w-centerWidth)/2;
        int centerY=(h-51)/2;
        int centerMid=h/2;
        gc.setForeground(muted);
        gc.drawLine(gap+leftWidth+2,centerMid,centerX-4,centerMid);
        gc.drawLine(centerX+centerWidth+4,centerMid, w-gap-rightWidth-2,centerMid);
        // Incoming arrows point to the selected object; outgoing arrows point away.
        gc.drawLine(centerX-11,centerMid-4,centerX-4,centerMid);
        gc.drawLine(centerX-11,centerMid+4,centerX-4,centerMid);
        int arrowX=w-gap-rightWidth-2;
        gc.drawLine(arrowX-7,centerMid-4,arrowX,centerMid);
        gc.drawLine(arrowX-7,centerMid+4,arrowX,centerMid);
        gc.setBackground(fill); gc.setForeground(fg);
        gc.fillRoundRectangle(centerX,centerY,centerWidth,51,10,10);
        gc.drawRoundRectangle(centerX,centerY,centerWidth,51,10,10);
        centered(gc,"SELECTED OBJECT",centerX,centerY+5,centerWidth,true);
        centered(gc,ellipsize(gc,center,centerWidth-14),centerX,centerY+25,centerWidth,false);
        paintColumn(gc,inbound,gap,leftWidth,h,"INCOMING");
        paintColumn(gc,outbound,w-gap-rightWidth,rightWidth,h,"OUTGOING");
    }
    private void paintColumn(GC gc,List<String> names,int x,int width,int height,String header) {
        gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
        centered(gc,header+" ("+names.size()+")",x,8,width,true);
        gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND));
        if(names.isEmpty()) { centered(gc,"—",x,height/2-6,width,false); return; }
        int max=3, count=Math.min(names.size(),max), top=Math.max(31,(height-(count*19 + (names.size()>max?17:0)))/2);
        for(int i=0;i<count;i++) centered(gc,ellipsize(gc,names.get(i),width-5),x,top+i*19,width,false);
        if(names.size()>max) centered(gc,"+"+(names.size()-max)+" more",x,top+count*19,width,true);
    }
    private static void centered(GC gc,String text,int x,int y,int w,boolean subdued) {
        int tw=gc.textExtent(text).x;
        gc.drawText(text, x+Math.max(0,(w-tw)/2),y,true);
    }
    private static String ellipsize(GC gc,String value,int width) {
        if(gc.textExtent(value).x<=width) return value;
        String suffix="…";
        for(int i=value.length()-1;i>=0;i--) {
            String shortened=value.substring(0,i)+suffix;
            if(gc.textExtent(shortened).x<=width) return shortened;
        }
        return suffix;
    }
}
