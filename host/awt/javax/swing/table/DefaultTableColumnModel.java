package javax.swing.table;

import java.util.ArrayList;
import java.util.List;

/** Host shim: Swing's DefaultTableColumnModel (an ordered column list; no listeners are notified). */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultTableColumnModel implements TableColumnModel, java.io.Serializable {
    protected final List<TableColumn> tableColumns = new ArrayList<>();
    protected javax.swing.ListSelectionModel selectionModel;
    protected int columnMargin = 1;
    protected boolean columnSelectionAllowed;
    private final List<javax.swing.event.TableColumnModelListener> listeners = new ArrayList<>();

    public DefaultTableColumnModel() {}

    /** JTable.createDefaultColumnsFromModel: one column per model column, header = column name. */
    public static DefaultTableColumnModel __fromModel(TableModel m) {
        DefaultTableColumnModel cm = new DefaultTableColumnModel();
        if (m != null) for (int i = 0; i < m.getColumnCount(); i++) {
            TableColumn c = new TableColumn(i);
            c.setHeaderValue(m.getColumnName(i));
            cm.addColumn(c);
        }
        return cm;
    }

    @Override public void addColumn(TableColumn c) { if (c != null) tableColumns.add(c); }
    @Override public void removeColumn(TableColumn c) { tableColumns.remove(c); }
    @Override public void moveColumn(int from, int to) { tableColumns.add(to, tableColumns.remove(from)); }
    @Override public void setColumnMargin(int m) { columnMargin = m; }
    @Override public int getColumnCount() { return tableColumns.size(); }
    @Override public java.util.Enumeration getColumns() { return java.util.Collections.enumeration(tableColumns); }
    @Override public int getColumnIndex(Object id) {
        for (int i = 0; i < tableColumns.size(); i++) if (java.util.Objects.equals(tableColumns.get(i).getIdentifier(), id)) return i;
        throw new IllegalArgumentException("Identifier not found");
    }
    @Override public TableColumn getColumn(int i) { return tableColumns.get(i); }
    @Override public int getColumnMargin() { return columnMargin; }
    @Override public int getColumnIndexAtX(int x) {
        for (int i = 0; i < tableColumns.size(); i++) {
            x -= tableColumns.get(i).getWidth();
            if (x < 0) return i;
        }
        return -1;
    }
    @Override public int getTotalColumnWidth() {
        int w = 0;
        for (TableColumn c : tableColumns) w += c.getWidth();
        return w;
    }
    @Override public void setColumnSelectionAllowed(boolean b) { columnSelectionAllowed = b; }
    @Override public boolean getColumnSelectionAllowed() { return columnSelectionAllowed; }
    @Override public int[] getSelectedColumns() { return new int[0]; }
    @Override public int getSelectedColumnCount() { return 0; }
    @Override public void setSelectionModel(javax.swing.ListSelectionModel m) { selectionModel = m; }
    @Override public javax.swing.ListSelectionModel getSelectionModel() { return selectionModel; }
    @Override public void addColumnModelListener(javax.swing.event.TableColumnModelListener l) { if (l != null) listeners.add(l); }
    @Override public void removeColumnModelListener(javax.swing.event.TableColumnModelListener l) { listeners.remove(l); }
}
