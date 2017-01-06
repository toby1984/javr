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
package de.codesourcery.javr.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.table.TableModel;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.undo.UndoableEdit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilationContext;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.CompilerSettings;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.EditorSettings.SourceElement;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.Main;
import de.codesourcery.javr.ui.config.IApplicationConfig;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import de.codesourcery.javr.ui.frames.EditorFrame;
import de.codesourcery.javr.ui.frames.MessageFrame;

public class EditorPanel extends JPanel
{
	private static final Logger LOG = Logger.getLogger(EditorFrame.class);

	public static final Duration RECOMPILATION_DELAY = Duration.ofMillis( 150 );

	private final JTextPane editor = new JTextPane();

	private final IApplicationConfigProvider appConfigProvider;
	
	private final Consumer<IApplicationConfig> configListener = config -> 
	{
	    setupStyles();
	};
	
	private boolean ignoreEditEvents;
	
	private final List<UndoableEdit> undoStack = new ArrayList<>();
	private int undoStackPtr=0;

	private IProject project;

	private final JLabel cursorPositionLabel = new JLabel("0");
	
	private final ASTTreeModel astTreeModel = new ASTTreeModel();
	private JFrame astWindow = null;
	private JFrame searchWindow = null;

	private JFrame symbolWindow = null;

	private final SymbolTableModel symbolModel = new SymbolTableModel();

	private final RecompilationThread recompilationThread = new RecompilationThread();

	protected Style STYLE_TOPLEVEL;
	protected Style STYLE_LABEL;
	protected Style STYLE_NUMBER;
	protected Style STYLE_REGISTER;
	protected Style STYLE_PREPROCESSOR;
	protected Style STYLE_MNEMONIC;
	protected Style STYLE_COMMENT;

	private CompilationUnit currentUnit = new CompilationUnit( new StringResource("dummy",  "" ) );

	private final MessageFrame messageFrame;
	
	private final SearchHelper searchHelper=new SearchHelper();

	protected class SearchHelper {

		private int currentPosition;
		private String term;

		public boolean searchBackward() 
		{
			if ( ! canSearch() ) 
			{
				return false;
			} 
			String text = editor.getText().toLowerCase();
			if ( currentPosition ==0 ) {
				System.out.println("At start of text, starting from end");
				currentPosition = text.length()-1;
			}            
			System.out.println("Starting to search  backwards @ "+currentPosition);

			int startIndex = 0;
			final String searchTerm = term.toLowerCase();
			int previousMatch = text.indexOf( searchTerm, 0 );
			boolean searchWrapped = false;
			while ( previousMatch != -1 && startIndex < ( text.length()-1 ) ) {
				final int match = text.indexOf( searchTerm , startIndex );
				if ( match == -1 || match >= (currentPosition-1) ) 
				{
					if ( searchWrapped || previousMatch < (currentPosition-1 ) ) {
						break;
					}
					startIndex = 0;
					currentPosition = text.length();
					searchWrapped = true;
					continue;                    
				} 
				previousMatch = match;
				startIndex = previousMatch+1;
			}
			if ( previousMatch != -1 ) {
				currentPosition = previousMatch;
				gotoMatch( currentPosition );
				return true;
			}
			return false;
		}

		private void gotoMatch(int cursorPos) 
		{
			editor.setCaretPosition( cursorPos );
			editor.select( cursorPos , cursorPos+term.length() );
			editor.requestFocus();
			currentPosition = cursorPos+1;
			System.out.println("Found match at "+cursorPos);            
		}

		public boolean searchForward() 
		{
			if ( ! canSearch() ) 
			{
				return false;
			}            
			final String text = editor.getText();
			if ( currentPosition >= text.length()) {
				System.out.println("At end of text, starting from 0");
				currentPosition = 0;
			}
			System.out.println("Starting to search @ "+currentPosition);
			final int nextMatch = text.substring( currentPosition , text.length() ).toLowerCase().indexOf( term.toLowerCase() );
			if ( nextMatch != -1 ) 
			{
				gotoMatch( currentPosition + nextMatch );
				return true;
			}
			System.out.println("No more matches");
			return false;
		}

		public void startFromBeginning() {
			System.out.println("Start from beginning");
			currentPosition = 0;
		}

		public boolean canSearch() 
		{
			final String text = editor.getText();
			return term != null && term.length() > 0 && text != null && text.length() != 0;
		}

		public void setTerm(String term) {
			this.term = term;
		}

		public String getTerm() {
			return term;
		}
	}

	protected static final class FilteredList<T> extends AbstractList<T> {

		private final List<T> unfiltered = new ArrayList<T>();
		private final List<T> filtered = new ArrayList<T>();

		private Function<T,Boolean> filterFunc = x -> true;

		public void setFilterFunc(Function<T,Boolean> filterFunc ) 
		{
			Validate.notNull(filterFunc, "filterFunc must not be NULL");
			this.filterFunc = filterFunc;
			doFilter();
		}

		private void doFilter() 
		{
			filtered.clear();
			for ( T elem : unfiltered ) 
			{
				if ( filterFunc.apply( elem ) == Boolean.TRUE ) {
					filtered.add( elem );
				}
			}
		}

		@Override
		public boolean addAll(Collection<? extends T> c) 
		{
			boolean result = false;
			for ( T elem : c ) {
				result |= add(elem);
			}
			return result;
		}

		@Override
		public boolean add(T e) 
		{
			Validate.notNull(e, "e must not be NULL");
			unfiltered.add( e );
			if ( filterFunc.apply( e ) == Boolean.TRUE ) {
				filtered.add(e);
				return true;
			}
			return false;
		}

		@Override
		public void clear() {
			filtered.clear();
			unfiltered.clear();
		}

		@Override
		public Iterator<T> iterator() {
			return filtered.iterator();
		}

		@Override
		public T get(int index) 
		{
			return filtered.get(index);
		}

		@Override
		public int size() {
			return filtered.size();
		}
	}

	protected class IndentFilter extends DocumentFilter 
	{
		private static final String NEWLINE = "\n";

		public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException
		{
			if ( isNewline( str ) ) 
			{
				str = addWhiteSpace(fb.getDocument(), offs);
			}
			super.insertString(fb, offs, replaceTabs(str) , a);
		}

		private boolean isNewline(String s) {
			return NEWLINE.equals( s );
		}

		private String replaceTabs(String in) {
			return in.replace("\t" ,  project.getConfig().getEditorIndentString() );
		}

		public void replace(FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException
		{
			if ( isNewline( str ) ) {
				str = addWhiteSpace(fb.getDocument(), offs);
			}
			super.replace(fb, offs, length, replaceTabs(str) , a);
		}

		private String addWhiteSpace(Document doc, int offset) throws BadLocationException
		{
			final StringBuilder whiteSpace = new StringBuilder("\n");
			final Element rootElement = doc.getDefaultRootElement();
			final int line = rootElement.getElementIndex( offset );

			int i = rootElement.getElement(line).getStartOffset();
			while (true)
			{
				final String temp = doc.getText(i, 1);

				if (temp.equals(" ") || temp.equals("\t"))
				{
					whiteSpace.append(temp);
					i++;
				}
				else {
					break;
				}
			}
			return whiteSpace.toString();
		}        
	}

	protected final class RecompilationThread extends Thread {

		private long lastChange = -1;

		private final AtomicBoolean terminate = new AtomicBoolean(false);

		private final Object SLEEP_LOCK = new Object();

		{
			setDaemon(true);
			setName("recompilation");
		}

		public void run() 
		{
			while ( ! terminate.get() ) 
			{
				long ts = -1;                    
				synchronized( SLEEP_LOCK ) 
				{
					try
					{
						SLEEP_LOCK.wait();
						ts = lastChange;
					}
					catch (InterruptedException e) { /* */ } 
				}

				if ( ts == -1 ) {
					continue;
				}

				boolean doRecompile = false;
				while( ! terminate.get() ) 
				{
					try { Thread.sleep( (int) RECOMPILATION_DELAY.toMillis() ); } catch(Exception e) { /* */ }

					synchronized( SLEEP_LOCK ) 
					{
						if( lastChange == ts ) 
						{
							lastChange = -1;
							doRecompile = true;
							break;
						} 
						ts = lastChange;
					}
				}

				if ( doRecompile ) 
				{
					try {
						SwingUtilities.invokeAndWait( () -> compile() );
					}
					catch(Exception e) {
						// ignore
					}
				}
			}
		}

		public void documentChanged() 
		{
			synchronized ( SLEEP_LOCK ) 
			{
				lastChange = System.currentTimeMillis();
				SLEEP_LOCK.notifyAll();
			}
		}
	}

	private final class SymbolTableModel implements TableModel {

		private final FilteredList<Symbol> symbols = new FilteredList<>();

		private final List<TableModelListener> listeners = new ArrayList<>();

		private String filterString = null;

		public void setSymbolTable(SymbolTable table)
		{
			Validate.notNull(table,"table must not be NULL");
			this.symbols.clear();
			final List<Symbol> allSymbolsSorted = table.getAllSymbolsSorted();
			this.symbols.addAll( allSymbolsSorted );
			tableChanged();
		}        

		public void clear() 
		{
			symbols.clear();
			setFilterString( this.filterString );
		}

		private void tableChanged() {
			final TableModelEvent ev = new TableModelEvent( this );
			listeners.forEach( l -> l.tableChanged( ev ) );
		}

		public void setFilterString(String s) 
		{
			this.filterString = s == null ? null : s.toLowerCase();
			final Function<Symbol,Boolean> func;
			if ( filterString == null ) 
			{
				func = symbol -> true;
			} else {
				func = symbol -> filterString == null ? Boolean.TRUE : Boolean.valueOf( symbol.name().value.toLowerCase().contains( filterString ) );
			}
			symbols.setFilterFunc( func );        
			tableChanged();
		}

		@Override
		public int getRowCount() {
			return symbols.size();
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
					return "Name";
				case 1:
					return "Type";
				case 2:
					return "Value";
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
			final Symbol symbol = symbols.get(rowIndex);
			switch( columnIndex ) {
				case 0:
					return symbol.name().value;
				case 1:
					return symbol.getType().toString();
				case 2:
					final Object value = symbol.getValue(); 
					return value == null ? "<no value>" : value.toString();
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
	}

	private final class ASTTreeModel implements TreeModel 
	{
		private final List<TreeModelListener> listeners = new ArrayList<>();

		private AST ast = new AST();

		public void setAST(AST ast) 
		{
			this.ast = ast;

			final TreeModelEvent ev = new TreeModelEvent(this, new TreePath(this.ast) );
			listeners.forEach( l -> l.treeStructureChanged( ev  ) );
		}

		public AST getAST() 
		{
			return ast;
		}

		@Override
		public AST getRoot() {
			return ast;
		}

		@Override
		public Object getChild(Object parent, int index) 
		{
			return ((ASTNode) parent).child(index);
		}

		@Override
		public int getChildCount(Object parent) {
			return ((ASTNode) parent).childCount();
		}

		@Override
		public boolean isLeaf(Object node) {
			return ((ASTNode) node).hasNoChildren();
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) 
		{
			final TreeModelEvent ev = new TreeModelEvent(this , path );
			listeners.forEach( l -> l.treeNodesChanged( ev  ) );
		}

		@Override
		public int getIndexOfChild(Object parent, Object child) {
			return ((ASTNode) parent).indexOf( (ASTNode) child);
		}

		@Override
		public void addTreeModelListener(TreeModelListener l) {
			listeners.add(l);
		}

		@Override
		public void removeTreeModelListener(TreeModelListener l) {
			listeners.remove(l);
		}
	}

	public EditorPanel(IProject project, CompilationUnit unit,IApplicationConfigProvider appConfigProvider,MessageFrame messageFrame) throws IOException 
	{
		Validate.notNull(project, "project must not be NULL");
		Validate.notNull(unit, "unit must not be NULL");
        Validate.notNull(appConfigProvider, "appConfigProvider must not be NULL");
        Validate.notNull(messageFrame, "messageFrame must not be NULL");
        this.messageFrame = messageFrame;
        this.appConfigProvider = appConfigProvider;
		this.project = project;
		this.currentUnit = unit;

		editor.addCaretListener( new CaretListener() 
		{
            @Override
            public void caretUpdate(CaretEvent e) 
            {
                cursorPositionLabel.setText( Integer.toString( e.getDot()  ) );
            }
		});
		editor.addKeyListener( new KeyAdapter() {

			@Override
			public void keyTyped(KeyEvent e) 
			{
				System.out.println("Typed: "+e.getKeyChar()+" , modifiers: "+e.getModifiersEx()+" , "+KeyEvent.CTRL_DOWN_MASK);
				if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0 ) 
				{
					final byte[] bytes = Character.toString( e.getKeyChar() ).getBytes();
					System.out.println("Bytes: "+bytes.length+" , "+Integer.toHexString( bytes[0] ) );
					if ( e.getKeyChar() == 6 ) // CTRL-F
					{
						toggleSearchWindow();
					} 
					else if ( e.getKeyChar() == 0x0b && searchHelper.canSearch() ) // CTRL-K 
					{
						searchHelper.searchForward();
					} 
					else if ( e.getKeyChar() == 0x02 && searchHelper.canSearch() ) // CTRL-B
					{
						searchHelper.searchBackward();
					}                     
					else if ( e.getKeyChar() == 0x07 ) // CTRL-G
					{
						gotoLine();
					}                    
					else if ( e.getKeyChar() == 0x0d ) // CTRL+S 
					{ 
						try {
							saveSource();
						} 
						catch (IOException e1) 
						{
							Main.fail(e1);
						}
					} else if ( e.getKeyChar() == 0x1a && undoStackPtr > 0 ) { // CTRL-Z
						final UndoableEdit edit = undoStack.get( --undoStackPtr );
						edit.undo();
					}  else if ( e.getKeyChar() == 0x19 && undoStackPtr < undoStack.size() ) { // CTRL-Y
						final UndoableEdit edit = undoStack.get(undoStackPtr++);
						edit.redo();
					}
				}
			}
		});

		editor.setFont(new Font("monospaced", Font.PLAIN, 12));
		final JPanel panel = new JPanel();
		panel.setLayout( new GridBagLayout() );

		// add toolbar
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.HORIZONTAL;
		cnstrs.weightx = 1.0; cnstrs.weighty=0;
		cnstrs.gridwidth=1; cnstrs.gridheight = 1 ;
		cnstrs.gridx = 0; cnstrs.gridy = 0;

		panel.add( createToolbar() , cnstrs );

		// editor        

		// ugly hack to adjust splitpane size after it has become visible
		addAncestorListener( new AncestorListener() {
            
            @Override
            public void ancestorRemoved(AncestorEvent event) { 
                appConfigProvider.removeChangeListener( configListener );
            }
            
            @Override
            public void ancestorMoved(AncestorEvent event) { }
            
            @Override
            public void ancestorAdded(AncestorEvent event) 
            {
                appConfigProvider.addChangeListener( configListener );
            }
        });
		
		cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1.0; cnstrs.weighty=1;
		cnstrs.gridwidth=1; cnstrs.gridheight = 1 ;
		cnstrs.gridx = 0; cnstrs.gridy = 1;

		final JScrollPane editorPane = new JScrollPane( editor );
		panel.add( editorPane , cnstrs );

	    setLayout( new GridBagLayout() );
        cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx = 1.0; cnstrs.weighty=1;
        cnstrs.gridwidth=1; cnstrs.gridheight = 1 ;
        cnstrs.gridx = 0; cnstrs.gridy = 0;
        
		add( panel , cnstrs );

		// setup styles
		setupStyles();

		// setup recompilation
		recompilationThread.start();

		setProject( project , currentUnit );
	}
	
	private void setupStyles() 
	{
        final StyleContext ctx = new StyleContext();

        final Style topLevelStyle = ctx.addStyle( "topLevelStyle" , null);
        STYLE_TOPLEVEL = topLevelStyle;

        STYLE_LABEL    = createStyle( "labelStyle" , SourceElement.LABEL, ctx );
        STYLE_NUMBER   = createStyle( "numberStyle" , SourceElement.NUMBER, ctx );
        STYLE_REGISTER = createStyle( "registerStyle" , SourceElement.REGISTER , ctx );
        STYLE_MNEMONIC = createStyle( "mnemonicStyle" , SourceElement.MNEMONIC, ctx );
        STYLE_COMMENT  = createStyle( "commentStyle" , SourceElement.COMMENT , ctx );     
        STYLE_PREPROCESSOR = createStyle( "preprocStyle" , SourceElement.PREPROCESSOR , ctx );   	    
	}
	
	private Style createStyle(String name,SourceElement sourceElement,StyleContext ctx) 
	{
	    final Color col = appConfigProvider.getApplicationConfig().getEditorSettings().getColor( sourceElement , Color.BLACK );
	    System.out.println("Element "+sourceElement+" has color "+col);
	    final Style style = ctx.addStyle( name , STYLE_TOPLEVEL );
	    style.addAttribute(StyleConstants.Foreground, col );
	    return style;
	}

	private Document createDocument() 
	{
		final Document doc = editor.getEditorKit().createDefaultDocument();

		// setup styles

		ignoreEditEvents = false;

		doc.addDocumentListener( new DocumentListener() 
		{
			@Override public void insertUpdate(DocumentEvent e) 
			{
				if ( ! ignoreEditEvents ) {
					recompilationThread.documentChanged();
				}
			}
			@Override public void removeUpdate(DocumentEvent e) 
			{ 
				if ( ! ignoreEditEvents ) {
					recompilationThread.documentChanged(); 
				}
			}
			@Override public void changedUpdate(DocumentEvent e) 
			{ 
				if ( ! ignoreEditEvents ) {
					recompilationThread.documentChanged();
				}
			}
		});

		// setup auto-indent
		//        ((AbstractDocument) doc).setDocumentFilter( new IndentFilter() );

		undoStack.clear();
		undoStackPtr = 0;
		
		doc.addUndoableEditListener( new UndoableEditListener() 
		{
			@Override
			public void undoableEditHappened(UndoableEditEvent e) 
			{
				if ( ! ignoreEditEvents ) 
				{
					if ( undoStackPtr == undoStack.size() ) {
						undoStackPtr++;
					}
					undoStack.add( e.getEdit() );
				}
			}
		});
		return doc;
	}

	private JToolBar createToolbar() 
	{
		final JToolBar result = new JToolBar(JToolBar.HORIZONTAL);

		result.add( button("Compile" , ev -> this.compile() ) );
		result.add( button("AST" , ev -> this.toggleASTWindow() ) );
		result.add( button("Symbols" , ev -> this.toggleSymbolTableWindow() ) );
		result.add( button("Upload to uC" , ev -> this.uploadToController() ) );
		result.add( new JLabel("Cursor pos:" ) );
		result.add( cursorPositionLabel );
		return result;
	}

	private void uploadToController() 
	{
		if ( project.canUploadToController() ) 
		{
			try 
			{
				project.uploadToController();
			} 
			catch(Exception e) 
			{
				LOG.error("UPLOAD failed",e);
				Main.fail("Upload failed",e);
			} 
		}
		else 
		{
			JOptionPane.showMessageDialog(null, "Upload not possible", "Program upload", JOptionPane.INFORMATION_MESSAGE );
		}
	}
	
	private void saveSource() throws IOException 
	{
		String text = editor.getText();
		text = text == null ? "" : text;

		final Resource resource = currentUnit.getResource();
		LOG.info("saveSource(): Saving source to "+currentUnit.getResource());
		try ( OutputStream out = resource.createOutputStream() ) 
		{
			out.write( text.getBytes( resource.getEncoding() ) );
		}
	}

	public void compile() 
	{
	    messageFrame.clearMessages();
	    currentUnit.clearMessages();
		symbolModel.clear();

		String text = editor.getText();
		text = text == null ? "" : text;

		// save source to file
		try {
			saveSource();
		} 
		catch(IOException e) 
		{
			LOG.error("compile(): Failed to save changes",e);
			currentUnit.addMessage( CompilationMessage.error( currentUnit, "Failed to save changes: "+e.getMessage()) );
			return;
		}
		
		// try to parse only this compilation unit
		// to get an AST suitable for syntax highlighting
        astTreeModel.setAST( new AST() );
		try 
		{
		    final CompilerSettings compilerSettings = new CompilerSettings();
		    final IObjectCodeWriter writer = new ObjectCodeWriter();
            final ICompilationContext context = new CompilationContext( project , writer , project , compilerSettings , project.getConfig() );
            ParseSourcePhase.parseWithoutIncludes( context, currentUnit , project );
		} 
		catch(Exception e) {
		    LOG.error("Parsing source failed",e);
		}
        astTreeModel.setAST( currentUnit.getAST() );
		
        doSyntaxHighlighting();		

		// assemble
		final CompilationUnit root = project.getCompileRoot();

		long assembleTime = 0;
		boolean compilationSuccessful = false;
		try 
		{
			final long start = System.currentTimeMillis();
			compilationSuccessful = project.compile();
			assembleTime = System.currentTimeMillis() -start;
		} 
		catch(Exception e) 
		{
			e.printStackTrace();
			root.addMessage( toCompilationMessage( currentUnit, e ) );
		}
		symbolModel.setSymbolTable( currentUnit.getSymbolTable() );    
		final List<CompilationMessage> allMessages = root.getMessages(true);
        messageFrame.addAll( allMessages );        

		final float seconds = assembleTime/1000f;
		final DecimalFormat DF = new DecimalFormat("#######0.0#");
		final String time = "Time: "+ assembleTime +" ms ";

		currentUnit.addMessage( CompilationMessage.info(currentUnit,time) );

		final String success = compilationSuccessful ? "successful" : "failed"; 
		final DateTimeFormatter df = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss");
		currentUnit.addMessage( CompilationMessage.info(currentUnit,"Compilation "+success+" ("+assembleTime+" millis) on "+df.format( ZonedDateTime.now() ) ) );
	}

	private static CompilationMessage toCompilationMessage(CompilationUnit unit,Exception e)
	{
		if ( e instanceof ParseException ) {
			return new CompilationMessage(unit,Severity.ERROR , e.getMessage() , new TextRegion( ((ParseException ) e).getOffset() , 0 ,-1 , -1 ) ); 
		} 
		return new CompilationMessage(unit,Severity.ERROR , e.getMessage() );
	}

	private void doSyntaxHighlighting() 
	{
		ignoreEditEvents = true;
		final String text = editor.getText();
		try 
		{
			final StyledDocument doc = editor.getStyledDocument();
			final IASTVisitor visitor = new IASTVisitor() 
			{
				@Override
				public void visit(ASTNode node, IIterationContext ctx) 
				{
					final TextRegion region = node.getTextRegion();
					if ( region != null ) 
					{
						Style style = null;
						if ( node instanceof PreprocessorNode ) {
							style = STYLE_PREPROCESSOR;
						} 
						else if ( node instanceof RegisterNode) {
							style = STYLE_REGISTER;
						} 
						else if ( node instanceof InstructionNode ) 
						{
							style = STYLE_MNEMONIC;
						} 
						else if ( node instanceof CommentNode ) 
						{
							style = STYLE_COMMENT;
						}     
						else if ( node instanceof LabelNode || node instanceof IdentifierNode) 
						{
							style = STYLE_LABEL;
						}      
						else if ( node instanceof NumberLiteralNode ) 
						{
							style = STYLE_NUMBER;
						}                      

						if ( style != null ) 
						{
							doc.setCharacterAttributes( region.start(), region.length() , style , true );
						}
					}
				}
			};
			astTreeModel.getAST().visitBreadthFirst( visitor );
		} finally {
			ignoreEditEvents = false;
		}
	}

	private JButton button(String label,ActionListener l) {
		final JButton astButton = new JButton( label );
		astButton.addActionListener( l );
		return astButton;
	}

	private void toggleASTWindow() 
	{
		if ( astWindow != null ) {
			astWindow.dispose();
			astWindow=null;
			return;
		}
		astWindow = createASTWindow();

		astWindow.pack();
		astWindow.setLocationRelativeTo( this );
		astWindow.setVisible( true );
	}

	private void toggleSearchWindow() 
	{
		if ( searchWindow != null ) {
			searchWindow.dispose();
			searchWindow=null;
			return;
		}
		searchWindow = createSearchWindow();

		searchWindow.pack();
		searchWindow.setLocationRelativeTo( this );
		searchWindow.setVisible( true );
	}    

	private void toggleSymbolTableWindow() 
	{
		if ( symbolWindow != null ) {
			symbolWindow.dispose();
			symbolWindow=null;
			return;
		}
		symbolWindow = createSymbolWindow();

		symbolWindow.pack();
		symbolWindow.setLocationRelativeTo( this );
		symbolWindow.setVisible( true );
	}

	private JFrame createASTWindow() 
	{
		final JFrame frame = new JFrame("AST");

		frame.addWindowListener( new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				frame.dispose();
				astWindow = null;
			}
		});

		final JTree tree = new JTree( astTreeModel );

		tree.setCellRenderer( new DefaultTreeCellRenderer() 
		{
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus)
			{
				final Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,hasFocus);
				if ( value instanceof ASTNode) 
				{
					final ASTNode node = (ASTNode) value;
					String text=node.getClass().getSimpleName();

					if ( node instanceof FunctionCallNode ) {
						text = ((FunctionCallNode) node).functionName.value+"(...)";
					} 
					else if ( node instanceof DirectiveNode) {
						text = "."+((DirectiveNode) node).directive.literal;
					} 
					else  if ( node instanceof PreprocessorNode) {
						text = "#"+((PreprocessorNode) node).type.literal;
					} else if ( node instanceof OperatorNode) {
						text = "Operator: "+((OperatorNode) node).type.getSymbol();
					} else  if ( node instanceof IdentifierNode) {
						text = "identifier: "+((IdentifierNode) node).name.value;
					}
					else if ( node instanceof StatementNode) {
						text = "statement";
					}                     
					else if ( node instanceof LabelNode) {
						text = "label:"+((LabelNode) node).identifier.value;
					} 
					else if ( node instanceof CommentNode) {
						text = "comment: "+((CommentNode) node).value;
					}                     
					else if ( node instanceof NumberLiteralNode ) 
					{
						final NumberLiteralNode node2 = (NumberLiteralNode) value;
						switch( node2.getType() ) {
							case BINARY:
								text = "0b"+Integer.toBinaryString( node2.getValue() );
								break;
							case DECIMAL:
								text = Integer.toString( node2.getValue() );
								break;
							case HEXADECIMAL:
								text = "0x"+Integer.toHexString( node2.getValue() );
								break;
							default:
								throw new RuntimeException("Unreachable code reached");
						}
					} 
					else if ( node instanceof InstructionNode ) 
					{
						text = "insn: "+((InstructionNode) node).instruction.getMnemonic();
					}
					else if ( node instanceof RegisterNode) 
					{
						text = "reg: "+((RegisterNode) node).register.toString();
					}

					setText( text + " -" + ((ASTNode) node).getTextRegion() );
				}
				return result;
			}
		});
		tree.setRootVisible( true );
		tree.setVisibleRowCount( 5 );

		final JPanel panel = new JPanel();
		panel.setLayout( new BorderLayout() );
		final JScrollPane pane = new JScrollPane();
		pane.getViewport().add( tree );
		panel.add( pane , BorderLayout.CENTER );
		frame.getContentPane().add( panel );

		return frame;
	}

	private JFrame createSearchWindow() 
	{
		final JFrame frame = new JFrame("Search");

		frame.addWindowListener( new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				frame.dispose();
				searchWindow = null;
			}
		});

		final JLabel label = new JLabel();
		label.setText("Enter text to search.");

		final JTextField filterField = new JTextField();
		if ( searchHelper.getTerm() != null ) {
			filterField.setText( searchHelper.getTerm() );
		}
		filterField.getDocument().addDocumentListener( new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) { resetLabel(); }

			@Override
			public void removeUpdate(DocumentEvent e) { resetLabel(); }

			@Override
			public void changedUpdate(DocumentEvent e) { resetLabel(); }

			private void resetLabel() 
			{
				label.setText( "Hit enter to start searching");
				searchHelper.startFromBeginning();
			}
		});

		filterField.addActionListener( ev -> 
		{
			searchHelper.setTerm( filterField.getText() );
			boolean foundMatch  = false;
			if ( searchHelper.canSearch() ) 
			{
				foundMatch = searchHelper.searchForward(); 
				if ( ! foundMatch )
				{
					searchHelper.startFromBeginning();
					foundMatch = searchHelper.searchForward();
				}
			}
			if ( foundMatch ) {
				label.setText("Hit enter to continue searching");
			} else {
				label.setText("No (more) matches.");
			}            
		});

		final JPanel panel = new JPanel();
		panel.setLayout( new BorderLayout() );
		panel.add( filterField , BorderLayout.NORTH );
		panel.add( label , BorderLayout.CENTER );
		frame.getContentPane().add( panel );
		frame.setPreferredSize( new Dimension(200,50 ) );
		return frame;
	}      

	private JFrame createSymbolWindow() 
	{
		final JFrame frame = new JFrame("Symbols");

		frame.addWindowListener( new WindowAdapter() {

			@Override
			public void windowClosing(WindowEvent e) {
				frame.dispose();
				symbolWindow = null;
			}
		});

		final JTextField filterField = new JTextField();

		filterField.addActionListener( ev -> 
		{
			symbolModel.setFilterString( filterField.getText() );
		});
		filterField.getDocument().addDocumentListener( new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) { symbolModel.setFilterString( filterField.getText() ); }

			@Override
			public void removeUpdate(DocumentEvent e) { symbolModel.setFilterString( filterField.getText() ); }

			@Override
			public void changedUpdate(DocumentEvent e) { symbolModel.setFilterString( filterField.getText() ); }
		});

		final JTable table= new JTable( symbolModel );

		final JPanel panel = new JPanel();
		panel.setLayout( new BorderLayout() );
		final JScrollPane pane = new JScrollPane();
		pane.getViewport().add( table );
		panel.add( filterField , BorderLayout.NORTH );
		panel.add( pane , BorderLayout.CENTER );

		frame.getContentPane().add( panel );
		return frame;
	}    

	private void gotoLine() {

		final String lineNo = JOptionPane.showInputDialog(null, "Enter line number", "Go to line", JOptionPane.QUESTION_MESSAGE );
		if ( StringUtils.isNotBlank( lineNo ) ) 
		{
			int no = -1;
			try {
				no = Integer.parseInt( lineNo );                    
			} catch (Exception e) {
				e.printStackTrace();
			}
			if ( no > 0 ) 
			{
			    String text = editor.getText();
			    if ( text == null ) {
			        text = "";
			    }
			    int offset = 0;
			    for ( final int len=text.length() ; no > 1 && offset < len ; offset++ ) {
			        final char c = text.charAt( offset );
			        if ( c == '\r' && (offset+1) < len && text.charAt(offset+1) == '\n' ) {
			            no--;
			            offset++;
			        } 
			        else if ( c == '\n' ) 
			        {
			            no--;
			        }
			    }
			    editor.setCaretPosition( offset );
				editor.requestFocus();
			}
		}
	}

	public void setProject(IProject project,CompilationUnit unit) throws IOException 
	{
		Validate.notNull(project, "project must not be NULL");
		Validate.notNull(unit, "unit must not be NULL");
		
		LOG.info("addWindows(): Now editing  "+unit.getResource());
		this.project = project;
		this.currentUnit = unit;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try ( InputStream in = unit.getResource().createInputStream() ) 
		{
			IOUtils.copy( in , out );
		}
		final byte[] data = out.toByteArray();
		final String source = new String(data, unit.getResource().getEncoding() );

		editor.setDocument( createDocument() );
		editor.setText( source );
		editor.setCaretPosition( 0 );
	}

	public void save(File file) throws FileNotFoundException 
	{
		try ( PrintWriter w = new PrintWriter(file ) ) 
		{
			if ( editor.getText() != null )  {
				w.write( editor.getText() );
			}
			currentUnit.addMessage( CompilationMessage.info( currentUnit , "Source saved to "+file.getAbsolutePath() ) );
		}
	}
	
	public IProject getProject() {
        return project;
    }
	
	public CompilationUnit getCompilationUnit() {
        return currentUnit;
    }
	
	public void setCursorPosition(int position) 
	{
	    editor.setCaretPosition( position );
	}
	
	public void gotoMessage(CompilationMessage message) 
	{
		final int len = editor.getText().length();
		if ( message.region != null && 0 <= message.region.start() && message.region.start() < len ) 
		{
			editor.setCaretPosition( message.region.start() );
			editor.requestFocus();
		}
	}
}