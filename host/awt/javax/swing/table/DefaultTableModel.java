package javax.swing.table;

import java.util.Vector;

/** Host shim: Swing's DefaultTableModel (rows as Vectors, column identifiers). */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultTableModel extends AbstractTableModel {
    protected Vector<Vector> dataVector = new Vector<>();
    protected Vector columnIdentifiers = new Vector();

    public DefaultTableModel() { this(0, 0); }
    public DefaultTableModel(int rowCount, int columnCount) {
        for (int i = 0; i < columnCount; i++) columnIdentifiers.add(null);
        setRowCount(rowCount);
    }
    public DefaultTableModel(Vector columnNames, int rowCount) {
        if (columnNames != null) columnIdentifiers = new Vector(columnNames);
        setRowCount(rowCount);
    }
    public DefaultTableModel(Object[] columnNames, int rowCount) { this(toVector(columnNames), rowCount); }
    public DefaultTableModel(Vector data, Vector columnNames) { setDataVector(data, columnNames); }
    public DefaultTableModel(Object[][] data, Object[] columnNames) { setDataVector(data, columnNames); }

    protected static Vector toVector(Object[] a) {
        Vector v = new Vector();
        if (a != null) for (Object o : a) v.add(o);
        return v;
    }

    public Vector getDataVector() { return dataVector; }

    public void setDataVector(Vector data, Vector columnNames) {
        dataVector = new Vector<>();
        if (data != null) for (Object r : data) dataVector.add(r instanceof Vector ? (Vector) r : new Vector());
        columnIdentifiers = columnNames == null ? new Vector() : new Vector(columnNames);
        justify();
        fireTableStructureChanged();
    }

    public void setDataVector(Object[][] data, Object[] columnNames) {
        Vector rows = new Vector();
        if (data != null) for (Object[] r : data) rows.add(toVector(r));
        setDataVector(rows, toVector(columnNames));
    }

    private void justify() {
        for (Vector r : dataVector) r.setSize(getColumnCount());
    }

    public void setColumnIdentifiers(Vector ids) { setDataVector(dataVector, ids); }
    public void setColumnIdentifiers(Object[] ids) { setColumnIdentifiers(toVector(ids)); }

    public void setColumnCount(int count) {
        columnIdentifiers.setSize(count);
        justify();
        fireTableStructureChanged();
    }

    public void addColumn(Object id) {
        columnIdentifiers.add(id);
        justify();
        fireTableStructureChanged();
    }

    public void setRowCount(int count) {
        int old = dataVector.size();
        if (count == old) return;
        while (dataVector.size() > count) dataVector.remove(dataVector.size() - 1);
        while (dataVector.size() < count) {
            Vector r = new Vector();
            r.setSize(getColumnCount());
            dataVector.add(r);
        }
        if (count > old) fireTableRowsInserted(old, count - 1);
        else fireTableRowsDeleted(count, old - 1);
    }
    public void setNumRows(int count) { setRowCount(count); }

    public void addRow(Vector row) { insertRow(dataVector.size(), row); }
    public void addRow(Object[] row) { addRow(toVector(row)); }
    public void insertRow(int index, Vector row) {
        Vector r = row == null ? new Vector() : new Vector(row);
        r.setSize(getColumnCount());
        dataVector.insertElementAt(r, index);
        fireTableRowsInserted(index, index);
    }
    public void insertRow(int index, Object[] row) { insertRow(index, toVector(row)); }
    public void removeRow(int index) {
        dataVector.remove(index);
        fireTableRowsDeleted(index, index);
    }
    public void moveRow(int start, int end, int to) {
        java.util.List<Vector> moved = new java.util.ArrayList<>(dataVector.subList(start, end + 1));
        for (int i = start; i <= end; i++) dataVector.remove(start);
        dataVector.addAll(to, moved);
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return dataVector.size(); }
    @Override public int getColumnCount() { return columnIdentifiers.size(); }
    @Override public String getColumnName(int column) {
        Object id = column < columnIdentifiers.size() ? columnIdentifiers.get(column) : null;
        return id == null ? super.getColumnName(column) : id.toString();
    }
    @Override public boolean isCellEditable(int row, int column) { return true; }
    @Override public Object getValueAt(int row, int column) { return dataVector.get(row).get(column); }
    @Override public void setValueAt(Object value, int row, int column) {
        dataVector.get(row).set(column, value);
        fireTableCellUpdated(row, column);
    }
}
