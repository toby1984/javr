/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.javr.ui.panels;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import org.apache.commons.lang3.Validate;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.ui.config.IModel;

public class OutlinePanel extends JPanel 
{
	private IModel<CompilationUnit> model;
	
	private final JTable symbolTable = new JTable();
	
	private boolean showGlobalLabels = true;
	private boolean showLocalLabels = false;
	private boolean showEqu = false;
	private boolean showMacros= false;
	
	private String pattern;
	
	private Consumer<Symbol> doubleClickListener = symbol -> {};
	
	public OutlinePanel(IModel<CompilationUnit> model) 
	{
		setModel(model);
		
		symbolTable.setDefaultRenderer(Symbol.class , new DefaultTableCellRenderer() 
		{
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,boolean hasFocus, int row, int column) 
			{
				final Component result = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				final Symbol symbol = (Symbol) value;
				setText( symbol.name().value );
				if ( symbol.getValue() != null ) {
					setToolTipText( symbol.getValue().toString() );
				} else {
					setToolTipText( null );
				}
				return result;
			}
		});
		
		symbolTable.setFillsViewportHeight( true );
		
		symbolTable.addMouseListener( new MouseAdapter() 
		{
			@Override
			public void mouseClicked(MouseEvent e) 
			{
				if ( e.getClickCount() ==2 && e.getButton() == MouseEvent.BUTTON1) 
				{
					final int rowIdx = symbolTable.rowAtPoint( e.getPoint() );
					if ( rowIdx >= 0 ) {
						final Optional<Symbol> symbol = ((MyRowModel) symbolTable.getModel()).row( rowIdx );
						if ( symbol.isPresent() && symbol.get().getTextRegion() != null ) 
						{
							doubleClickListener.accept( symbol.get() );
						}
					}
				}
			}
		});
		final JScrollPane pane = new JScrollPane( symbolTable );
		
		setLayout( new GridBagLayout() );
		setPreferredSize( new Dimension(300,200 ) );
		
		final GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1;
		cnstrs.weighty = 1;
		cnstrs.gridwidth = cnstrs.gridheight = 1;
		cnstrs.gridx=0;
		cnstrs.gridy=0;	
		
		add( pane , cnstrs );
	}
	
	public void setShowGlobalLabels(boolean yesNo) {
		this.showGlobalLabels = yesNo;
		modelChanged();
	}
	
	public void setShowEqu(boolean showEqu) {
		this.showEqu = showEqu;
		modelChanged();
	}
	
	public void setShowLocalLabels(boolean yesNo) {
		this.showLocalLabels = yesNo;
		modelChanged();
	}
	
	public boolean isShowLocalLabels() {
		return showLocalLabels;
	}
	
	public void setShowMacros(boolean showMacros) {
		this.showMacros = showMacros;
		modelChanged();
	}
	
	public boolean isShowEqu() {
		return showEqu;
	}
	
	public boolean isShowGlobalLabels() {
		return showGlobalLabels;
	}
	
	public boolean isShowMacros() {
		return showMacros;
	}
	
	public void setPattern(String pat) {
		this.pattern = pat == null ? null : pat.toLowerCase();
		modelChanged();
	}
	
	public String getPattern() {
		return pattern;
	}
	
	private boolean isVisible(Symbol s) {
		
		if ( pattern != null && ! s.name().value.toLowerCase().contains( pattern ) ) {
			return false;
		}
		return  ( showLocalLabels && s.hasType( Type.ADDRESS_LABEL ) && Identifier.isLocalGlobalIdentifier( s.name() ) ) ||
				( showGlobalLabels && s.hasType( Type.ADDRESS_LABEL ) && ! Identifier.isLocalGlobalIdentifier( s.name() ) ) ||
				( showEqu && s.hasType( Type.EQU ) ) |
				( showMacros && s.hasType( Type.PREPROCESSOR_MACRO ) );
	}
	
	private Optional<SymbolTable> symbols() 
	{
		final CompilationUnit currentUnit = model.getObject();
		if ( currentUnit == null ) {
			return Optional.empty();
		}
		SymbolTable table = currentUnit.getSymbolTable();
		return Optional.ofNullable( table == null ? null : table.getTopLevelTable() );
	}
	
	private Stream<Symbol> symbolStream() 
	{
		return symbols().map( table -> table.getAllSymbolsSorted().stream() ).orElse( Stream.empty() )
				.filter( this::isVisible );
	}
	
	public void setModel(IModel<CompilationUnit> model) {
		
		Validate.notNull(model, "model must not be NULL");
		this.model = model;
		modelChanged();
	}
	
	private void modelChanged() 
	{
		symbolTable.setModel( new MyRowModel() );
	}
	
	public void setDoubleClickListener(Consumer<Symbol> doubleClickListener) {
		this.doubleClickListener = doubleClickListener;
	}
	
	private final class MyRowModel implements TableModel 
	{
		private final ArrayList<TableModelListener> listener = new ArrayList<>();
		
		public Optional<Symbol> row(int row) {
			return symbolStream().skip(row).findFirst();
		}
		
		@Override
		public int getRowCount() 
		{
			return (int) symbolStream().count();
		}

		@Override
		public int getColumnCount() { return 1; }

		@Override
		public String getColumnName(int columnIndex) { return "Name"; }
		
		@Override
		public Class<?> getColumnClass(int columnIndex) { return Symbol.class; }

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) 
		{ 
			return row(rowIndex).get();
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			throw new UnsupportedOperationException("setValueAt() not implemented");
		}

		@Override
		public void addTableModelListener(TableModelListener l) {
			listener.add( l );
		}

		@Override
		public void removeTableModelListener(TableModelListener l) {
			listener.remove( l );
		}
	};
}