package java.awt;

/** Android shim of java.awt.AWTEvent (hierarchy as in the JDK). */
public abstract class AWTEvent extends java.util.EventObject {
    protected int id;
    protected boolean consumed;

    public AWTEvent(Object source, int id) {
        super(source != null ? source : new Object());
        this.id = id;
    }
    public int getID() { return id; }
    protected void consume() { consumed = true; }
    protected boolean isConsumed() { return consumed; }
    public void setSource(Object source) { this.source = source; }
    public String paramString() { return ""; }
}
