package javax.swing.table;

/** Host shim: Swing's TableColumn (model index, header, renderers, widths; no painting). */
public class TableColumn implements java.io.Serializable {
    protected int modelIndex, width = 75, minWidth = 15, maxWidth = Integer.MAX_VALUE, preferredWidth = 75;
    protected Object identifier, headerValue;
    protected TableCellRenderer headerRenderer, cellRenderer;
    protected Object cellEditor;
    protected boolean isResizable = true;

    public TableColumn() { this(0); }
    public TableColumn(int modelIndex) { this(modelIndex, 75); }
    public TableColumn(int modelIndex, int width) { this(modelIndex, width, null, null); }
    public TableColumn(int modelIndex, int width, TableCellRenderer cellRenderer, Object cellEditor) {
        this.modelIndex = modelIndex;
        this.width = this.preferredWidth = width;
        this.cellRenderer = cellRenderer;
        this.cellEditor = cellEditor;
    }

    public int getModelIndex() { return modelIndex; }
    public void setModelIndex(int i) { modelIndex = i; }
    public Object getIdentifier() { return identifier != null ? identifier : headerValue; }
    public void setIdentifier(Object id) { identifier = id; }
    public Object getHeaderValue() { return headerValue; }
    public void setHeaderValue(Object v) { headerValue = v; }
    public TableCellRenderer getHeaderRenderer() { return headerRenderer; }
    public void setHeaderRenderer(TableCellRenderer r) { headerRenderer = r; }
    public TableCellRenderer getCellRenderer() { return cellRenderer; }
    public void setCellRenderer(TableCellRenderer r) { cellRenderer = r; }
    public int getWidth() { return width; }
    public void setWidth(int w) { width = Math.max(minWidth, Math.min(maxWidth, w)); }
    public int getPreferredWidth() { return preferredWidth; }
    public void setPreferredWidth(int w) { preferredWidth = Math.max(minWidth, Math.min(maxWidth, w)); }
    public int getMinWidth() { return minWidth; }
    public void setMinWidth(int w) { minWidth = Math.max(w, 0); }
    public int getMaxWidth() { return maxWidth; }
    public void setMaxWidth(int w) { maxWidth = Math.max(minWidth, w); }
    public boolean getResizable() { return isResizable; }
    public void setResizable(boolean r) { isResizable = r; }
    public void sizeWidthToFit() {}
}
