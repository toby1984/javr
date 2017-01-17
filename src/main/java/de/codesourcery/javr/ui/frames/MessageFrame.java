/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.ui.frames;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;

public class MessageFrame extends JInternalFrame implements IWindow 
{
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
	
	public static final int COLUMN_TIMESTAMP = 0;
	public static final int COLUMN_LOCATION = 1;
	public static final int COLUMN_SEVERITY = 2;
	public static final int COLUMN_MESSAGE = 3;
	
    public static final String WINDOW_ID = "messagewindow";

    private final MessageTableModel messageModel = new MessageTableModel();
    
    final JTable errorTable = new JTable( messageModel );
    
    private Consumer<CompilationMessage> doubleClickListener = msg -> {};
    

    protected final class MessageTableModel implements TableModel 
    {
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
            return 4;
        }

        private void assertValidColumn(int columnIndex) {
            if ( columnIndex < 0 || columnIndex > 3 ) {
                throw new RuntimeException("Invalid column: "+columnIndex);
            }
        }
        @Override
        public String getColumnName(int columnIndex) 
        {
            switch(columnIndex) 
            {
            	case 0:
            		return "Time";
	            case 1:
                    return "Location";
                case 2:
                    return "Severity";
                case 3:
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
            switch( columnIndex ) 
            {
            	case 0:
            		return DATE_FORMAT.format( msg.getTimestamp() );
                case 1:
                    if ( msg.region == null ) {
                        return "<unknown>";
                    }
                    return msg.region.toString();
                case 2:
                    return msg.severity.toString();
                case 3:
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

    public MessageFrame(String title) 
    {
        super(title,true,true,true);

        errorTable.setAutoResizeMode( JTable.AUTO_RESIZE_OFF );

        errorTable.setFillsViewportHeight(true);
        errorTable.setPreferredScrollableViewportSize( new Dimension(800,400));
        
        errorTable.setDefaultRenderer( String.class , new DefaultTableCellRenderer() 
        {
        	
        	private int stringWidth(String text) {
        		return (int) (getFontMetrics( getFont() ).stringWidth( text )*1.5);
        	}
        	
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) 
            {
                final Component result = super.getTableCellRendererComponent(errorTable, value, isSelected, hasFocus, row, column);
                if ( column == 2 ) {
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
                final int columnNameWidth = stringWidth( errorTable.getColumnName( column ) );
                final int textWidth = stringWidth( value.toString() );
                final Dimension size = new Dimension( Math.max( textWidth, columnNameWidth ) , getPreferredSize().height );
                final TableColumn col = errorTable.getColumnModel().getColumn( column );
                
                col.setMinWidth( size.width );
                col.setMaxWidth( size.width );
                setPreferredSize( size );
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

        getContentPane().setLayout( new GridBagLayout() );
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.gridheight = 1; cnstrs.gridwidth = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;
        cnstrs.weightx = 1; cnstrs.weighty = 1;
        final JScrollPane pane = new JScrollPane( errorTable );
        getContentPane().add( pane , cnstrs );
    }

    public void setDoubleClickListener(Consumer<CompilationMessage> doubleClickListener) 
    {
        Validate.notNull(doubleClickListener, "doubleClickListener must not be NULL");
        this.doubleClickListener = doubleClickListener;
    }

    public void clearMessages() 
    {
        messageModel.clear();
    }

    public void add(CompilationMessage msg) {
        messageModel.add( msg );
    }

    public void addAll(Collection<CompilationMessage> msgs) {
        messageModel.addAll( msgs );
    }

    @Override
    public String getWindowId() {
        return WINDOW_ID;
    }    
}