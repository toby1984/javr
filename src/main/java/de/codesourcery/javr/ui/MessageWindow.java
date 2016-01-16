package de.codesourcery.javr.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JInternalFrame;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Location;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.ui.config.IModel;

public class MessageWindow extends JInternalFrame {

    private final MessageTableModel messageModel = new MessageTableModel();
    private Consumer<CompilationMessage> doubleClickListener = msg -> {};
    
    protected final class MessageTableModel implements TableModel {

        private final List<CompilationMessage> errors = new ArrayList<>();

        private final List<TableModelListener> listeners = new ArrayList<>();

        public void add(CompilationMessage msg) 
        {
            Validate.notNull(msg,"msg must not be NULL");
            this.errors.add(msg);
            int idx = this.errors.size();
            final TableModelEvent ev = new TableModelEvent( this , idx ,idx );
            listeners.forEach( l -> l.tableChanged( ev ) );
        }

        public void addAll(Collection<CompilationMessage> msg) 
        {
            Validate.notNull(msg,"msg must not be NULL");
            final int start = errors.size();
            this.errors.addAll(msg);
            final int end = this.errors.size();
            final TableModelEvent ev = new TableModelEvent( this , start,end );
            listeners.forEach( l -> l.tableChanged( ev ) );
        }        

        public void clear() 
        {
            errors.clear();
            final TableModelEvent ev = new TableModelEvent( this );
            listeners.forEach( l -> l.tableChanged( ev ) );
        }

        @Override
        public int getRowCount() {
            return errors.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        private void assertValidColumn(int columnIndex) {
            if ( columnIndex < 0 || columnIndex > 3 ) {
                throw new RuntimeException("Invalid column: "+columnIndex);
            }
        }
        @Override
        public String getColumnName(int columnIndex) 
        {
            switch(columnIndex) {
                case 0:
                    return "Location";
                case 1:
                    return "Severity";
                case 2:
                    return "Message";
                default:
                    throw new RuntimeException("Invalid column: "+columnIndex);
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) 
        {
            assertValidColumn(columnIndex);
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) 
        {
            final CompilationMessage msg = errors.get(rowIndex);
            switch( columnIndex ) {
                case 0:

                    if ( msg.region == null ) {
                        return "<unknown>";
                    }
                    return msg.region.toString();
                case 1:
                    return msg.severity.toString();
                case 2:
                    return msg.message;
                default:
                    throw new RuntimeException("Invalid column: "+columnIndex);                 
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addTableModelListener(TableModelListener l) {
            listeners.add(l);
        }

        @Override
        public void removeTableModelListener(TableModelListener l) {
            listeners.remove(l);
        }

        public CompilationMessage getRow(int row) {
            return errors.get( row );
        }
    }
    
    public MessageWindow(String title) 
    {
        super(title,true,true,true);
        
        final JTable errorTable = new JTable( messageModel );

        errorTable.setDefaultRenderer( String.class , new DefaultTableCellRenderer() 
        {
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) 
            {
                final Component result = super.getTableCellRendererComponent(errorTable, value, isSelected, hasFocus, row, column);
                if ( column == 1 ) {
                    final CompilationMessage msg = messageModel.getRow( row );
                    switch ( msg.severity ) 
                    {
                        case ERROR:
                            result.setBackground( Color.RED );
                            break;
                        case WARNING:
                            result.setBackground( Color.YELLOW );
                            break;
                        default:
                            result.setBackground( Color.WHITE );
                    }
                } else {
                    result.setBackground( Color.WHITE );
                }
                return result;
            }
        } );
        errorTable.addMouseListener( new MouseAdapter() 
        {
            @Override
            public void mouseClicked(MouseEvent e) 
            {
                if ( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) 
                {
                    int row = errorTable.rowAtPoint( e.getPoint() );
                    if ( row != -1 ) {
                        CompilationMessage msg = messageModel.getRow( row );
                        doubleClickListener.accept( msg );
                    }
                }
            }
        });        
    }
    
    public void setDoubleClickListener(Consumer<CompilationMessage> doubleClickListener) 
    {
        Validate.notNull(doubleClickListener, "doubleClickListener must not be NULL");
        this.doubleClickListener = doubleClickListener;
    }
    
    public void add(CompilationMessage msg) {
        messageModel.add( msg );
    }
    
    public void addAll(Collection<CompilationMessage> msgs) {
        messageModel.addAll( msgs );
    }    
}
