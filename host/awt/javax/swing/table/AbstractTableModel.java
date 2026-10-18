package javax.swing.table;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

/** Host shim: Swing's AbstractTableModel (listener list, fire* notifications, spreadsheet column names). */
public abstract class AbstractTableModel implements TableModel, java.io.Serializable {
    protected final java.util.List<TableModelListener> listenerList = new java.util.concurrent.CopyOnWriteArrayList<>();

    public String getColumnName(int column) {
        StringBuilder sb = new StringBuilder();
        for (int c = column; c >= 0; c = c / 26 - 1) sb.insert(0, (char) ('A' + c % 26));
        return sb.toString();
    }

    public int findColumn(String name) {
        for (int i = 0; i < getColumnCount(); i++) if (getColumnName(i).equals(name)) return i;
        return -1;
    }

    public Class<?> getColumnClass(int column) { return Object.class; }
    public boolean isCellEditable(int row, int column) { return false; }
    public void setValueAt(Object value, int row, int column) {}

    public void addTableModelListener(TableModelListener l) { if (l != null) listenerList.add(l); }
    public void removeTableModelListener(TableModelListener l) { listenerList.remove(l); }
    public TableModelListener[] getTableModelListeners() { return listenerList.toArray(new TableModelListener[0]); }

    public void fireTableDataChanged() { fireTableChanged(new TableModelEvent(this)); }
    public void fireTableStructureChanged() { fireTableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW)); }
    public void fireTableRowsInserted(int first, int last) { fireTableChanged(new TableModelEvent(this, first, last, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT)); }
    public void fireTableRowsUpdated(int first, int last) { fireTableChanged(new TableModelEvent(this, first, last, TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE)); }
    public void fireTableRowsDeleted(int first, int last) { fireTableChanged(new TableModelEvent(this, first, last, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE)); }
    public void fireTableCellUpdated(int row, int column) { fireTableChanged(new TableModelEvent(this, row, row, column)); }
    public void fireTableChanged(TableModelEvent e) { for (TableModelListener l : listenerList) l.tableChanged(e); }
}
