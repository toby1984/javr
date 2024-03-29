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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.JViewport;
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
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument.AttributeUndoableEdit;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;
import de.codesourcery.javr.assembler.CompilationContext;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.CompilerSettings;
import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ObjectCodeWriter;
import de.codesourcery.javr.assembler.PrettyPrinter;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.AbstractArchitecture;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Identifier;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.IntNumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.phases.ParseSourcePhase;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.assembler.symbols.Symbol.Type;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.CaretPositionTracker;
import de.codesourcery.javr.ui.CaretPositionTracker.CaretPosition;
import de.codesourcery.javr.ui.EditorSettings.SourceElement;
import de.codesourcery.javr.ui.IDEMain;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.SourceMap;
import de.codesourcery.javr.ui.config.IApplicationConfig;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import de.codesourcery.javr.ui.frames.EditorFrame;
import de.codesourcery.javr.ui.frames.MessageFrame;
import de.codesourcery.swing.autocomplete.AutoCompleteBehaviour;
import de.codesourcery.swing.autocomplete.AutoCompleteBehaviour.DefaultAutoCompleteCallback;
import de.codesourcery.swing.autocomplete.AutoCompleteBehaviour.InitialUserInput;

public abstract class EditorPanel extends JPanel
{
    private static final Logger LOG = Logger.getLogger(EditorFrame.class);

    public static final Duration RECOMPILATION_DELAY = Duration.ofMillis( 500 );

    private final JScrollPane editorPane;
    private final JTextPane editor = new JTextPane();
    private final GutterPanel gutterPanel;
    private final StatusLinePanel statusLinePanel;

    private final EditorFrame topLevelWindow;

    private final AutoCompleteBehaviour<Symbol> autoComplete = new AutoCompleteBehaviour<>();

    private final IApplicationConfigProvider appConfigProvider;

    private final Consumer<IApplicationConfig> configListener = config ->
    {
        setupStyles();
    };

    private final SourceMap sourceMap = new SourceMap( editor::getText );

    private final ShadowDOM frontDOM = new ShadowDOM();
    private final ShadowDOM backDOM = new ShadowDOM();

    private ShadowDOM currentDOM = frontDOM;

    private boolean ignoreEditEvents;
    private boolean indentFilterEnabled=true;

    private final UndoManagerWrapper undoManager = new UndoManagerWrapper();

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
    protected Style STYLE_HIGHLIGHTED;
    protected Style STYLE_TODO;

    private CompilationUnit currentUnit = new CompilationUnit( new StringResource("dummy",  "" ) );

    private final MessageFrame messageFrame;

    private final SearchHelper searchHelper=new SearchHelper();

    private ASTNode highlight;
    private boolean controlKeyPressed;

    private boolean wasCompiledAtLeastOnce = false;
    private final List<Runnable> afterCompilation = new ArrayList<>();

    protected class MyMouseListener extends MouseAdapter
    {
        private final Point point = new Point();

        @Override
        public void mouseClicked(MouseEvent e)
        {
            if ( e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1 )
            {
                final IdentifierNode node = getNodeOnlyIfCtrl( e );
                if ( node != null )
                {
                    final Symbol symbol = getSymbol( node );
                    if ( symbol != null && symbol.getTextRegion() != null )
                    {
                        final TextRegion region = symbol.getTextRegion();
                        if ( symbol.getCompilationUnit().hasSameResourceAs( currentUnit ) ) {
                            setSelection( region );
                        }
                        else
                        {
                            try
                            {
                                final EditorPanel editor = topLevelWindow.openEditor( project , symbol.getCompilationUnit() );
                                SwingUtilities.invokeLater( () -> editor.setSelection( region ) );
                            }
                            catch (IOException e1)
                            {
                                LOG.error("mouseClicked(): Failed to open editor",e1);
                                IDEMain.showError( "Failed to open editor", e1 );
                            }
                        }
                    }
                }
            }
        }

        private Symbol getSymbol(IdentifierNode node)
        {
            Symbol result = node.getSymbol();
            if ( result == null )
            {
                final SymbolTable table = currentUnit.getSymbolTable().getTopLevelTable();
                result = table.maybeGet( node.name ).orElse( null );
                if ( result == null )
                {
                    // maybe this is the name of a local variable.
                    //
                    // Traverse the AST backwards until we find the next global label while looking for a match,
                    // if this fails try traversing the AST forwards until we reach the next global label
                    StatementNode statement = node.getStatement();
                    while ( true )
                    {
                        final List<LabelNode> labels = statement.findLabels();
                        boolean foundGlobalLabel = false;
                        for ( LabelNode ln : labels )
                        {
                            if ( ln.isGlobal() )
                            {
                                foundGlobalLabel = true;
                                result = table.maybeGet( Identifier.newLocalGlobalIdentifier( ln.identifier , node.name ) ).orElse( null );
                                if ( result != null ) {
                                    return result;
                                }
                            }
                        }
                        final int previousIdx = statement.getParent().indexOf( statement )-1;
                        if ( foundGlobalLabel || previousIdx < 0 ) {
                            break;
                        }
                        statement = (StatementNode) statement.getParent().child( previousIdx );
                    }
                }
            }
            return result;
        }

        @Override
        public void mouseMoved(MouseEvent e)
        {
            final IdentifierNode node = getIdentifierNode( e );
            String toolTipText = null;
            if ( node != null )
            {
                final Symbol symbol = getSymbol( node );
                if ( symbol != null ) {
                    final long value = AbstractArchitecture.toIntValue( symbol.getValue() );
                    if ( value != AbstractArchitecture.VALUE_UNAVAILABLE )
                    {
                        final List<String> commentLines = new ArrayList<>();
                        final String msg = symbol.name() + " = " + value + " (0x" + Integer.toHexString( (int) value ) + ")";
                        if ( symbol.getNode() != null )
                        {
                            Optional<ASTNode> predecessor = symbol.getNode().getStatement().getPredecessor();
                            while (predecessor.isPresent() && predecessor.get() instanceof StatementNode s && s.isCommentOnlyLine())
                            {
                                commentLines.add( s.children().stream().map( x -> ((CommentNode) x).value ).collect( Collectors.joining() ) );
                                predecessor = s.getPredecessor();
                            }
                            Collections.reverse( commentLines );
                        }
                        if ( commentLines.isEmpty() )
                        {
                            toolTipText = msg;
                        }
                        else
                        {
                            final String comments = commentLines.stream().map( EditorPanel::htmlEscape ).collect( Collectors.joining("<br>"));
                            toolTipText = "<html>" + comments + "<br>" + htmlEscape(msg) + "</html>";
                        }
                    }
                }
            }
            editor.setToolTipText( toolTipText );
            if ( controlKeyPressed ) {
                setHighlight( node );
            }
        }

        private IdentifierNode getIdentifierNode(MouseEvent e)
        {
            point.x = e.getX();
            point.y = e.getY();
            final int pos = editor.viewToModel( point );
            ASTNode node = null;
            if ( pos >= 0 )
            {
                node = astTreeModel.getAST().getNodeAtOffset( pos );
            }
            return node == null || node.getTextRegion() == null || !(node instanceof IdentifierNode)? null : (IdentifierNode) node;
        }

        private IdentifierNode getNodeOnlyIfCtrl(MouseEvent e)
        {
            return controlKeyPressed ? getIdentifierNode( e ) : null;
        }
    }

    protected class SearchHelper
    {
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
                currentPosition = text.length()-1;
            }

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
        }

        public boolean searchForward()
        {
            if ( ! canSearch() )
            {
                return false;
            }
            final String text = editor.getText();
            if ( currentPosition >= text.length()) {
                currentPosition = 0;
            }
            final int nextMatch = text.substring( currentPosition , text.length() ).toLowerCase().indexOf( term.toLowerCase() );
            if ( nextMatch != -1 )
            {
                gotoMatch( currentPosition + nextMatch );
                return true;
            }
            return false;
        }

        public void startFromBeginning() {
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
            if ( indentFilterEnabled )
            {
                super.insertString(fb, offs, replaceTabs(str), a);
            } else {
                super.insertString(fb,offs,str,a);
            }
        }

        private boolean isNewline(String s) {
            return NEWLINE.equals( s );
        }

        private String replaceTabs(String in) {
            return in.replace("\t" ,  project.getConfig().getEditorIndentString() );
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException
        {
            if ( indentFilterEnabled )
            {
                final int oldLength = editor.getDocument().getLength();
                fb.remove(offset, length);
                documentLengthDecreased(oldLength - length);
            } else {
                super.remove(fb,offset,length);
            }
        }

        private void documentLengthDecreased(int newLength) {
            frontDOM.truncate( newLength );
            backDOM.truncate( newLength);
        }

        @Override
        public void replace(FilterBypass fb, int offs, int toDeleteLength, String origReplacement, AttributeSet a) throws BadLocationException
        {
            if ( ! indentFilterEnabled ) {
                super.replace(fb,offs,toDeleteLength,origReplacement,a);
                return;
            }

            final String newReplacement;
            if ( isNewline( origReplacement ) )
            {
                newReplacement = "\n  ";
            }
            else
            {
                newReplacement = replaceTabs( origReplacement );

            }
            final int oldLength = editor.getDocument().getLength();

            super.replace( fb, offs, toDeleteLength, newReplacement, a );

            if ( toDeleteLength > newReplacement.length() )
            {
                final int delta = toDeleteLength - newReplacement.length();
                documentLengthDecreased( oldLength - delta );
            }
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

        public void documentChanged(DocumentEvent event)
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
            return 8;
        }

        private void assertValidColumn(int columnIndex) {
            if ( columnIndex < 0 || columnIndex > 7 ) {
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
                    return "Symbol Type";
                case 2:
                    return "Object Type";
                case 3:
                    return "Object Size";
                case 4:
                    return "Segment";
                case 5:
                    return "Value";
                case 6:
                    return "Node";
                case 7:
                    return "Compilation unit";
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
                    return symbol.getObjectType().toString();
                case 3:
                    return Integer.toString( symbol.getObjectSize() );
                case 4:
                    final Segment segment = symbol.getSegment();
                    return segment == null ? "--" : segment.toString();
                case 5:
                    final Object value = symbol.getValue();
                    return value == null ? "<no value>" : value.toString();
                case 6:
                    return symbol.getNode() == null ? null : symbol.getNode().toString();
                case 7:
                    return symbol.getCompilationUnit().getResource().toString();
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

    public EditorPanel(IProject project, EditorFrame topLevelWindow , CompilationUnit unit,IApplicationConfigProvider appConfigProvider,MessageFrame messageFrame,
                       CaretPositionTracker caretTracker) throws IOException
    {
        Validate.notNull(project, "project must not be NULL");
        Validate.notNull(unit, "unit must not be NULL");
        Validate.notNull(appConfigProvider, "appConfigProvider must not be NULL");
        Validate.notNull(messageFrame, "messageFrame must not be NULL");
        Validate.notNull(caretTracker,"caretTracker must not be NULL");
        this.messageFrame = messageFrame;
        this.appConfigProvider = appConfigProvider;
        this.project = project;
        this.currentUnit = unit;
        this.topLevelWindow = topLevelWindow;
        this.caretTracker = caretTracker;

        // symbol auto completion callback
        autoComplete.setCallback( new DefaultAutoCompleteCallback<Symbol>()
        {
            private Symbol previousGlobalSymbol;

            private final char[] separatorChars = new char[] {'(',')',','};

            private boolean matches(Identifier name,String userInput)
            {
                for ( int i = 0 , matchCount = 0 , len = name.value.length() < userInput.length() ? name.value.length() : userInput.length() ; i < len ; i++ )
                {
                    final char c = Character.toLowerCase( name.value.charAt( i ) );
                    if ( c == userInput.charAt(i) )
                    {
                        matchCount++;
                        if ( matchCount == 3 ) {
                            return true;
                        }
                    } else {
                        break;
                    }
                }
                if ( name.value.toLowerCase().contains( userInput ) ) {
                    return true;
                }
                return false;
            }

            private boolean matches(Symbol symbol,String userInput)
            {
                if ( previousGlobalSymbol != null )
                {
                    if ( symbol.isLocalLabel() )
                    {
                        if ( symbol.getGlobalNamePart().equals( previousGlobalSymbol.name() ) )
                        {
                            if ( matches( symbol.getLocalNamePart() , userInput) ) {
                                return true;
                            }
                        }
                    }
                    else if ( matches( symbol.name() , userInput ) ) // global label 
                    {
                        return true;
                    }
                }
                else
                {
                    // no previous global symbol, only consider global labels
                    if ( symbol.isGlobalLabel() && matches( symbol.name() , userInput ) )
                    {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected boolean isSeparatorChar(char c)
            {
                if ( Character.isWhitespace( c ) ) {
                    return true;
                }
                for ( int i = 0, len = separatorChars.length ; i < len ; i++ )
                {
                    if ( c == separatorChars[i] ) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public List<Symbol> getProposals(String input)
            {
                final String lower = input.toLowerCase();

                final List<Symbol> globalMatches = new ArrayList<>();
                final List<Symbol> localMatches = new ArrayList<>();

                final SymbolTable globalTable = currentUnit.getSymbolTable().getTopLevelTable();
                globalTable.visitSymbols( (symbol) ->
                                          {
                                              switch( symbol.getType() )
                                              {
                                                  case ADDRESS_LABEL:
                                                      if ( matches(symbol , lower ) )
                                                      {
                                                          if ( symbol.isLocalLabel() ) {
                                                              localMatches.add(symbol);
                                                          } else {
                                                              globalMatches.add(symbol);
                                                          }
                                                      }
                                                      break;
                                                  case EQU:
                                                  case PREPROCESSOR_MACRO:
                                                      if ( symbol.name().value.toLowerCase().contains( lower ) )
                                                      {
                                                          globalMatches.add( symbol );
                                                      }
                                                      break;
                                                  default:
                                                      break;
                                              }

                                              return Boolean.TRUE;
                                          });

                globalMatches.sort( (a,b) -> a.name().value.compareTo( b.name().value ) );
                localMatches.sort( (a,b) -> a.getLocalNamePart().value.compareTo( b.getLocalNamePart().value ) );

                final List<Symbol> result = new ArrayList<>( globalMatches.size() + localMatches.size() );
                result.addAll( localMatches );
                result.addAll( globalMatches );
                return result;
            }

            @Override
            public String getStringToInsert(Symbol value)
            {
                if ( value.isLocalLabel() ) {
                    return value.getLocalNamePart().value;
                }
                return value.name().value;
            }

            @Override
            public InitialUserInput getInitialUserInput(JTextComponent editor, int caretPosition)
            {
                previousGlobalSymbol = null;
                final ASTNode node = astTreeModel.getAST().getNodeAtOffset( caretPosition-1 );
                if ( node != null )
                {
                    final SymbolTable globalTable = currentUnit.getSymbolTable().getTopLevelTable();
                    node.searchBackwards( n ->
                                          {
                                              if ( n instanceof LabelNode)
                                              {
                                                  if ( ((LabelNode) n).isGlobal() )
                                                  {
                                                      final Optional<Symbol> symbol = globalTable.maybeGet( ((LabelNode) n).identifier , Type.ADDRESS_LABEL );
                                                      if ( symbol.isPresent() )
                                                      {
                                                          previousGlobalSymbol = symbol.get();
                                                          return true;
                                                      }
                                                  }
                                              }
                                              return false;
                                          } );
                } else {
                    System.err.println("Failed to find AST node for current offset ?");
                }
                return super.getInitialUserInput(editor, caretPosition);
            }
        });

        autoComplete.setListCellRenderer( new DefaultListCellRenderer() {

            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                final Symbol symbol = (Symbol) value;
                setText( symbol.isLocalLabel() ? symbol.getLocalNamePart().value : symbol.name().value );
                return result;
            };
        });

        autoComplete.setInitialPopupSize( new Dimension(250,200 ) );
        autoComplete.setVisibleRowCount( 10 );

        editor.setFont( new Font(Font.MONOSPACED, Font.PLAIN, 12) );
        editor.addCaretListener( new CaretListener()
        {
            @Override
            public void caretUpdate(CaretEvent e)
            {
                cursorPositionLabel.setText( Integer.toString( e.getDot()  ) );
                if ( currentUnit != null && ! ignoreEditEvents )
                {
                    caretTracker.rememberCaretPosition( e.getDot(), currentUnit );
                }
            }
        });
        final MyMouseListener mouseListener = new MyMouseListener();

        editor.addMouseMotionListener( mouseListener);
        editor.addMouseListener( mouseListener );
        editor.addKeyListener( new KeyAdapter()
        {

            @Override
            public void keyPressed(KeyEvent e)
            {
                if ( isCtrlDown( e ) && e.getKeyCode() == KeyEvent.VK_W ) {
                    EditorPanel.this.close( true );
                }
                else if ( e.getKeyCode() == KeyEvent.VK_CONTROL ) {
                    controlKeyPressed = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e)
            {
                if ( e.getKeyCode() == KeyEvent.VK_CONTROL ) {
                    setHighlight( null );
                    controlKeyPressed = false;
                }
            }

            private boolean isCtrlDown(KeyEvent e)
            {
                return ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) != 0;
            }

            private boolean isAltDown(KeyEvent e)
            {
                return ( e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK ) != 0;
            }

            /**
             * Returns a {@link TextRegion} for the line that holds a given
             * caret position.
             *
             * Note that the returned region <b>include</b> the EOL character at the end of the line (if any).
             *
             * @param caretPosition
             * @return region describing the current line or <code>NULL</code> if the text editor is empty.
             */
            private TextRegion getLineAt(int caretPosition)
            {
                final Optional<SourceMap.Line> line = sourceMap.getLineByOffset(caretPosition);
                return line.map(l ->
                {
                    final int col = 1 + caretPosition - l.startOffset;
                    return new TextRegion(l.startOffset,l.endOffset-l.startOffset,l.lineNum,col);
                }).orElseGet(() ->
                {
                    LOG.error("getLineAt(): Found no line for caret position "+caretPosition);
                    return null;
                });
            }

            @Override
            public void keyTyped(KeyEvent e)
            {
                if ( isCtrlDown(e) )
                {
                    final byte[] bytes = Character.toString( e.getKeyChar() ).getBytes();
                    if ( e.getKeyChar() == 6 ) // CTRL-F ... search
                    {
                        toggleSearchWindow();
                    }
                    else if ( e.getKeyChar() == 0x12 ) // CTRL-R   ... delete to end of line
                    {
                        final TextRegion line = getLineAt( editor.getCaretPosition() );
                        if ( line != null && editor.getText().charAt( line.start() ) != '\n' )
                        {
                            try
                            {
                                final int toRemove = line.end() - editor.getCaretPosition();
                                editor.getDocument().remove( editor.getCaretPosition() , toRemove );
                            }
                            catch (BadLocationException e1) {
                                LOG.error("keyTyped(): Failed to remove()",e1);
                                IDEMain.showError( "Failed to remove()", e1 );
                            }
                        }
                    }
                    else if ( e.getKeyChar() == 0x04 ) // CTRL-D   ... delete line
                    {
                        final TextRegion line = getLineAt( editor.getCaretPosition() );
                        if ( line != null )
                        {
                            restoreCaretPositionAfter( () -> {
                                try
                                {
                                    editor.getDocument().remove( line.start(), line.length()+1 );
                                }
                                catch (BadLocationException e1)
                                {
                                    LOG.error("keyTyped(): Failed to remove()",e1);
                                    IDEMain.showError( "Failed to remove()", e1 );
                                }
                            });
                        }
                    }
                    else if ( e.getKeyChar() == 0x0b && searchHelper.canSearch() ) // CTRL-K ... search forward
                    {
                        searchHelper.searchForward();
                    }
                    else if ( e.getKeyChar() == 0x02 && searchHelper.canSearch() ) // CTRL-B ... search backwards
                    {
                        searchHelper.searchBackward();
                    }
                    else if ( e.getKeyChar() == 0x07 ) // CTRL-G ... goto line
                    {
                        gotoLine();
                    }
                    else if ( e.getKeyChar() == 0x0d ) // CTRL+S ... save 
                    {
                        try {
                            saveSource();
                        }
                        catch (IOException e1)
                        {
                            IDEMain.showError("Failed to save file",e1);
                        }
                    } else if ( e.getKeyChar() == 0x1a && undoManager.canUndo() ) { // CTRL-Z

                        restoreCaretPositionAfter( () -> {
                            undoManager.undo();
                            e.consume();
                        });
                    }  else if ( e.getKeyChar() == 0x19 && undoManager.canRedo() ) { // CTRL-Y
                        restoreCaretPositionAfter( () -> {
                            undoManager.redo();
                            e.consume();
                        });
                    }
                }
            }
        });

        editor.setFont(new Font("monospaced", Font.PLAIN, 12));
        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );

        // add toolbar
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.gridx = 0; cnstrs.gridy = 0;
        cnstrs.gridwidth=2; cnstrs.gridheight = 1 ;
        cnstrs.weightx = 1.0; cnstrs.weighty=0;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;

        panel.add( createToolbar() , cnstrs );

        // gutter
        editorPane = new JScrollPane( editor );
        gutterPanel = new GutterPanel( editorPane, this, appConfigProvider );

        final int gutterWidth = 50;
        gutterPanel.setMinimumSize( new Dimension(gutterWidth,10) );
        gutterPanel.setMaximumSize( new Dimension(gutterWidth,5000) );

        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(0,0,0,0);
        cnstrs.gridx = 0; cnstrs.gridy = 1;
        cnstrs.gridwidth=1; cnstrs.gridheight = 1 ;
        cnstrs.weightx = 0; cnstrs.weighty=1;
        cnstrs.fill = GridBagConstraints.VERTICAL;
        panel.add( gutterPanel, cnstrs );

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
        cnstrs.insets = new Insets(0,0,0,0);
        cnstrs.gridx = 1; cnstrs.gridy = 1;
        cnstrs.gridwidth=1; cnstrs.gridheight = 1 ;
        cnstrs.weightx = 1.0; cnstrs.weighty=1;
        cnstrs.fill = GridBagConstraints.BOTH;
        panel.add( editorPane , cnstrs );

        // status line
        statusLinePanel = new StatusLinePanel( sourceMap,editor );

        final int statusLineHeight = 16;
        statusLinePanel.setMinimumSize( new Dimension(10,statusLineHeight) );
        statusLinePanel.setMaximumSize( new Dimension(5000,statusLineHeight) );

        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(0,0,0,0);
        cnstrs.gridx = 0; cnstrs.gridy = 2;
        cnstrs.gridwidth=2; cnstrs.gridheight = 1 ;
        cnstrs.weightx = 1; cnstrs.weighty=0;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        panel.add( statusLinePanel, cnstrs );

        // add panel we used for composition
        setLayout( new GridBagLayout() );

        cnstrs = new GridBagConstraints();
        cnstrs.gridx = 0; cnstrs.gridy = 0;
        cnstrs.gridwidth=1; cnstrs.gridheight = 1 ;
        cnstrs.weightx = 1.0; cnstrs.weighty=1;
        cnstrs.fill = GridBagConstraints.BOTH;

        add( panel , cnstrs );

        // setup styles
        setupStyles();

        // setup recompilation
        recompilationThread.start();

        setProject( project , currentUnit );
    }

    private void restoreCaretPositionAfter(Runnable r)
    {
        final int oldPos = editor.getCaretPosition();
        r.run();
        try {
            editor.setCaretPosition( oldPos );
        } catch(IllegalArgumentException e) {
            // can't help it, probably the last line got deleted or something else
        }
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
        STYLE_TODO = createStyle( "todoStyle" , SourceElement.TODO , ctx );
        STYLE_PREPROCESSOR = createStyle( "preprocStyle" , SourceElement.PREPROCESSOR , ctx );

        // highlight
        STYLE_HIGHLIGHTED = ctx.addStyle( "highlight", topLevelStyle );
        STYLE_HIGHLIGHTED.addAttribute(StyleConstants.Foreground, Color.BLUE);
        STYLE_HIGHLIGHTED.addAttribute(StyleConstants.Underline, Boolean.TRUE );
    }

    private Style createStyle(String name,SourceElement sourceElement,StyleContext ctx)
    {
        final Color col = appConfigProvider.getApplicationConfig().getEditorSettings().getColor( sourceElement );
        final Style style = ctx.addStyle( name , STYLE_TOPLEVEL );
        style.addAttribute(StyleConstants.Foreground, col );
        return style;
    }

    private Document createDocument()
    {
        final Document doc = editor.getEditorKit().createDefaultDocument();

        // setup styles
        ignoreEditEvents = false;

        // setup auto-indent
        ((AbstractDocument) doc).setDocumentFilter( new IndentFilter() );

        doc.addDocumentListener( new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                if ( ! ignoreEditEvents ) {
                    lastEditLocation = e.getOffset();
                    recompilationThread.documentChanged(e);
                }
                sourceMap.invalidate();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                if ( ! ignoreEditEvents )
                {
                    lastEditLocation = e.getOffset();
                    recompilationThread.documentChanged(e);
                }
                sourceMap.invalidate();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                if ( ! ignoreEditEvents ) {
                    lastEditLocation = e.getOffset();
                    recompilationThread.documentChanged(e);
                }
                sourceMap.invalidate();
            }
        });

        undoManager.discardAllEdits();

        doc.addUndoableEditListener( new UndoableEditListener()
        {
            @Override
            public void undoableEditHappened(UndoableEditEvent e)
            {
                if ( ! ignoreEditEvents )
                {
                    if ( e.getEdit() instanceof AttributeUndoableEdit || e.getEdit().getClass().getName().contains("StyleChangeUndoableEdit") ) {
                        return;
                    }
                    undoManager.undoableEditHappened( e );
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
        result.add( button("Goto last edit" , ev -> this.gotoLastEditLocation() ) );
        result.add( button("Goto previous" , ev -> setCaretPosition( caretTracker.getPreviousCaretPosition() ) ) );
        result.add( button("Goto next" , ev -> setCaretPosition( caretTracker.getNextCaretPosition() ) ) );

        result.add( button("Indent" , ev -> this.indentSources() ) );
        result.add( button("Symbols" , ev -> this.toggleSymbolTableWindow() ) );
        result.add( button("Upload to uC" , ev -> this.uploadToController() ) );

        result.add( button("Show as avr-as" , ev ->
        {
            hidePrettyPrint();
            showPrettyPrint(true);
        } ) );

        result.add( button("Pretty print" , ev ->
        {
            hidePrettyPrint();
            showPrettyPrint(false);
        } ) );

        result.add( new JLabel("Cursor pos:" ) );
        result.add( cursorPositionLabel );
        return result;
    }

    private final CaretPositionTracker caretTracker;

    private int lastEditLocation = -1;

    private PrettyPrintWindow prettyPrintWindow;

    private final class PrettyPrintWindow extends JFrame {

        private JTextArea textArea = new JTextArea();

        public boolean gnuSyntax = true;

        public void astChanged()
        {
            final PrettyPrinter printer = new PrettyPrinter();
            try {
                printer.setGNUSyntax( gnuSyntax );
                final String source = printer.prettyPrint( getCompilationUnit().getAST() );
                textArea.setText( source );
            }
            catch(Exception e)
            {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                try ( PrintWriter pw = new PrintWriter( out ) ) {
                    e.printStackTrace( pw );
                }
                final String stacktrace = new String( out.toByteArray() );
                textArea.setText( stacktrace );
            }
            textArea.setCaretPosition( 0 );
        }

        public PrettyPrintWindow()
        {
            super("Pretty print");
            setMinimumSize( new Dimension(400,200 ) );

            getContentPane().setLayout( new GridBagLayout() );

            textArea.setEditable( false );

            GridBagConstraints cnstrs = new GridBagConstraints();
            cnstrs.fill = GridBagConstraints.BOTH;
            cnstrs.gridx = 0;
            cnstrs.gridy = 0;
            cnstrs.gridheight = 1;
            cnstrs.gridwidth = 1;
            cnstrs.weightx = 1;
            cnstrs.weighty = 1;
            cnstrs.insets = new Insets(0,0,0,0);
            getContentPane().add( new JScrollPane( textArea ) , cnstrs );
            pack();
            setVisible( true );
            textArea.setFont( new Font( Font.MONOSPACED , getFont().getStyle() , getFont().getSize() ) );
            setDefaultCloseOperation( JFrame.DO_NOTHING_ON_CLOSE );
            addWindowListener( new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent e)
                {
                    prettyPrintWindow = null;
                    dispose();
                }
            });
        }
    }

    private boolean isPrettyPrintShown() {
        return prettyPrintWindow != null;
    }

    private void hidePrettyPrint()
    {
        if ( isPrettyPrintShown() )
        {
            prettyPrintWindow.dispose();
        }
    }

    private void showPrettyPrint(boolean gnuSyntax)
    {
        if ( ! isPrettyPrintShown() )
        {
            prettyPrintWindow = new PrettyPrintWindow();
        }
        prettyPrintWindow.gnuSyntax = gnuSyntax;
        prettyPrintWindow.astChanged();
        prettyPrintWindow.toFront();
    }

    private void gotoLastEditLocation()
    {
        if ( lastEditLocation != -1 )
        {
            runAfterCompilation( () ->
                                 {
                                     if ( lastEditLocation != -1 )
                                     {
                                         editor.setCaretPosition( lastEditLocation );
                                         editor.requestFocus();
                                     }
                                 });
        } else {
            editor.requestFocus();
        }
    }

    private void setCaretPosition(CaretPosition position)
    {
        boolean caretMoved = false;
        if ( position != null )
        {
            if ( currentUnit.hasSameResourceAs( position.unit ) )
            {
                setCaretPosition( position.offset );
                caretMoved = true;
            }
            else
            {
                // TODO: Switch to editor panel for the compilation unit the caret was in
                try
                {
                    final EditorPanel editorPanel = topLevelWindow.openEditor( project, position.unit );
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        if ( ! caretMoved )
        {
            editor.requestFocus();
        }
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
                IDEMain.showError("Upload to uC failed",e);
            }
        }
        else
        {
            IDEMain.showError( "Program upload failed",
                               "Compilation failed, project uses incompatible output format or no upload command configured " );
        }
    }

    private void saveSource() throws IOException
    {
        String text = editor.getText();
        text = text == null ? "" : text;

        final Resource resource = currentUnit.getResource();
        LOG.info("saveSource(): Saving source to "+currentUnit.getResource());
        try ( OutputStream out = currentUnit.getResource().createOutputStream() )
        {
            final byte[] bytes = text.getBytes( resource.getEncoding() );
            out.write( bytes );
        }
    }

    public void compile()
    {
        highlight = null;

        messageFrame.clearMessages();
        currentUnit.clearMessages();
        symbolModel.clear();

        // save source to file
        try {
            saveSource();
        }
        catch(IOException e)
        {
            LOG.error("compile(): Failed to save changes",e);
            currentUnit.addMessage( CompilationMessage.error( currentUnit, "Failed to save changes: "+e.getMessage()) );
            IDEMain.showError( "Failed to save changes", e );
            return;
        }

        // parse only this compilation unit
        // to get an AST suitable for syntax highlighting that does
        // NOT nodes for expanded macros/includes like the regular compilation does
        astTreeModel.setAST( new AST() );

        // also do not use the current compilation unit here as this will
        // trigger "duplicate symbol" errors during the actual compilation
        // later on
        final CompilationUnit tmpUnit = new CompilationUnit( currentUnit.getResource() );

        final long parseStart =  System.currentTimeMillis();
        try
        {
            final CompilerSettings compilerSettings = new CompilerSettings();
            final IObjectCodeWriter writer = new ObjectCodeWriter();
            final SymbolTable globalSymbolTable = new SymbolTable( SymbolTable.GLOBAL ); // fake global symbol table so we don't fail parsing because of duplicate symbols already in the real one
            final ICompilationContext context = new CompilationContext( tmpUnit , globalSymbolTable , writer , project , compilerSettings , project.getConfig() );
            ParseSourcePhase.parseWithoutIncludes( context, tmpUnit , project );
        }
        catch(Exception e) {
            LOG.error("Parsing source failed",e);
            IDEMain.showError( "Parsing source failed", e );
        }
        astTreeModel.setAST( tmpUnit.getAST() );
        final long parseEnd =  System.currentTimeMillis();

        // do syntax highlighting
        doSyntaxHighlighting();
        final long highlightEnd =  System.currentTimeMillis();

        // assemble
        final CompilationUnit root = project.getCompileRoot();

        boolean compilationSuccessful = false;
        try
        {
            compilationSuccessful = project.compile();
        }
        catch(Exception e)
        {
            LOG.error("compile(): Compilation failed "+e.getMessage(), LOG.isDebugEnabled() ? e : null );
            currentUnit.addMessage( toCompilationMessage( currentUnit, e ) );
        }
        symbolModel.setSymbolTable( currentUnit.getSymbolTable() );

        final long compileEnd =  System.currentTimeMillis();

        // generation info message about compilation outcome
        final long parseTime = parseEnd - parseStart;
        final long highlightTime = highlightEnd - parseEnd;
        final long compileTime = compileEnd - highlightEnd;

        final String assembleTime = "parsing: "+parseTime+" ms,highlighting: "+highlightTime+" ms,compile: "+compileTime+" ms";

        final String success = compilationSuccessful ? "successful" : "failed";
        final DateTimeFormatter df = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss");
        currentUnit.addMessage( CompilationMessage.info(currentUnit,"Compilation "+success+" ("+assembleTime+") on "+df.format( ZonedDateTime.now() ) ) );

        if ( isPrettyPrintShown() ) {
            showPrettyPrint( prettyPrintWindow.gnuSyntax );
        }

        messageFrame.addAll( root.getMessages(true) );

        gutterPanel.repaint();

        wasCompiledAtLeastOnce = true;

        // execute deferred callbacks that were waiting on compilation to complete
        if ( ! afterCompilation.isEmpty() )
        {
            for ( Runnable r : afterCompilation ) {
                try {
                    r.run();
                } catch(Exception e) {
                    IDEMain.showError("Closure failed to execute: "+r,e);
                }
            }
            afterCompilation.clear();
        }
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
        doSyntaxHighlighting( astTreeModel.getAST() );
    }

    private void doSyntaxHighlighting(ASTNode subtree)
    {
        updateShadowDOM( dom -> {
            subtree.visitBreadthFirst( (node, ctx) -> setNodeStyle( node, dom ) );
        });
    }

    private void updateShadowDOM(Consumer<ShadowDOM> domCallback)
    {
        final boolean oldState = ignoreEditEvents;
        ignoreEditEvents = true;
        try
        {
            final ShadowDOM frontBuffer = currentDOM;
            final ShadowDOM backBuffer = frontBuffer == frontDOM ? backDOM : frontDOM;

            // render style to back buffer
            domCallback.accept( backBuffer );

            // apply changes to StyledDocuments
            backBuffer.applyDelta( editor.getStyledDocument(), frontBuffer );

            // swap back & front buffer
            currentDOM = backBuffer;
        }
        catch(RuntimeException e)
        {
            LOG.error("doSyntaxHighlighting(): Failed ",e);
            IDEMain.showError( "Highlighting failed",e );
        } finally {
            ignoreEditEvents = oldState;
        }
    }

    private TextRegion getVisibleRegion()
    {
        final JViewport viewport = editorPane.getViewport();
        Point startPoint = viewport.getViewPosition();
        Dimension size = viewport.getExtentSize();
        Point endPoint = new Point(startPoint.x + size.width, startPoint.y + size.height);

        int start = editor.viewToModel( startPoint );
        int end = editor.viewToModel( endPoint );
        return new TextRegion(start,end-start,0,0);
    }

    private void setNodeStyle(ASTNode node,ShadowDOM shadowDOM)
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
                final String comment = ((CommentNode) node).value;
                if ( comment.contains("TODO") ) {
                    style = STYLE_TODO;
                } else {
                    style = STYLE_COMMENT;
                }
            }
            else if ( node instanceof LabelNode || node instanceof IdentifierNode)
            {
                style = STYLE_LABEL;
            }
            else if ( node instanceof IntNumberLiteralNode)
            {
                style = STYLE_NUMBER;
            }
            if ( style != null )
            {
                shadowDOM.setCharacterAttributes( region, style );
            } else {
                shadowDOM.setCharacterAttributes( region, STYLE_TOPLEVEL );
            }
        }
    }

    private void setHighlight(ASTNode newHighlight)
    {
        if ( this.highlight == newHighlight ) {
            return;
        }

        if ( newHighlight != null && newHighlight.getTextRegion() == null ) {
            throw new IllegalStateException("Cannot highlight a node that has no text region assigned");
        }

        if ( this.highlight != null && newHighlight != null && this.highlight.getTextRegion().equals( newHighlight.getTextRegion() ) ) {
            return;
        }

        if ( this.highlight != null ) {
            doSyntaxHighlighting( this.highlight );
        }
        this.highlight = newHighlight;
        if ( newHighlight != null )
        {
            final TextRegion region = newHighlight.getTextRegion();
            ignoreEditEvents = true;
            try {
                updateShadowDOM( dom -> dom.setCharacterAttributes( region, STYLE_HIGHLIGHTED ) );
            } finally {
                ignoreEditEvents = false;
            }
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

    private void indentSources()
    {
        restoreCaretPositionAfter( () -> setText( indent( this.editor.getText() ) ) );
    }

    public static String indent(String text)
    {
        final int indent = 2;

        if ( text == null ) {
            return text;
        }

        final String[] lines = text.split("\n");
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++)
        {
            final String line = lines[i];
            if ( line.length() == 0 ) {
                result.append("\n");
                continue;
            }
            if ( isWhitespace( line.charAt(0) ) )
            {
                int j = 0;
                int len=line.length();
                for (  ; j < len && isWhitespace( line.charAt(j) ); j++ ) {
                }
                for ( int k = indent ;  k > 0 ; k-- ) {
                    result.append( ' ');
                }
                for ( ; j < len ; j++ ) {
                    result.append( line.charAt(j) );
                }
            } else {
                result.append( line );
            }
            // append trailing newline except for when we're on the last line
            if ( (i+1) < lines.length ) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
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

        final MouseAdapter mouseListener = new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                if ( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 )
                {
                    final TreePath path = tree.getClosestPathForLocation( e.getX() ,  e.getY() );
                    if ( path != null && path.getPath() != null && path.getPath().length >= 1 )
                    {
                        final ASTNode node = (ASTNode) path.getLastPathComponent();
                        if ( node.getTextRegion() != null ) {
                            setSelection( node.getTextRegion() );
                        }
                    }
                }
            }
        };
        tree.addMouseListener( mouseListener);

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
                        text = "OperatorNode: "+((OperatorNode) node).type.getSymbol();
                    } else  if ( node instanceof IdentifierNode) {
                        text = "IdentifierNode: "+((IdentifierNode) node).name.value;
                    }
                    else if ( node instanceof StatementNode) {
                        text = "StatementNode";
                    }
                    else if ( node instanceof LabelNode) {
                        text = "LabelNode:"+((LabelNode) node).identifier.value;
                    }
                    else if ( node instanceof CommentNode) {
                        text = "CommentNode: "+((CommentNode) node).value;
                    }
                    else if ( node instanceof IntNumberLiteralNode)
                    {
                        final IntNumberLiteralNode node2 = (IntNumberLiteralNode) value;
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

                    setText( text + " - " + ((ASTNode) node).getTextRegion() + " - merged: "+((ASTNode) node).getMergedTextRegion() );
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
        final boolean[] wrap = { true };

        final JFrame frame = new JFrame("Search");

        frame.addWindowListener( new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                frame.dispose();
                searchWindow = null;
            }
        });

        final JLabel label = new JLabel("Enter text to search.");

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

        filterField.addKeyListener( new KeyAdapter() {

            @Override
            public void keyReleased(KeyEvent e)
            {
                if ( e.getKeyCode() == KeyEvent.VK_ESCAPE )
                {
                    e.consume();
                    toggleSearchWindow();
                }
            }
        });
        filterField.addActionListener( ev ->
                                       {
                                           searchHelper.setTerm( filterField.getText() );
                                           boolean foundMatch  = false;
                                           if ( searchHelper.canSearch() )
                                           {
                                               foundMatch = searchHelper.searchForward();
                                               if ( ! foundMatch && wrap[0] )
                                               {
                                                   searchHelper.startFromBeginning();
                                                   foundMatch = searchHelper.searchForward();
                                               }
                                           }
                                           frame.toFront();
                                           filterField.requestFocus();
                                           if ( foundMatch ) {
                                               label.setText("Hit enter to continue searching");
                                           } else {
                                               label.setText("No (more) matches.");
                                           }
                                       });

        final JPanel panel = new JPanel();
        panel.setLayout( new GridBagLayout() );

        // add search textfield
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weightx = 0.33;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.gridx = 0; cnstrs.gridy = 0;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;

        panel.add( filterField , cnstrs );

        // add 'wrap?' checkbox
        final JCheckBox wrapCheckbox = new JCheckBox("Wrap?" , wrap[0] );
        wrapCheckbox.addActionListener( ev ->
                                        {
                                            wrap[0] = wrapCheckbox.isSelected();
                                        });
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weightx = 0.33;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.gridx = 0; cnstrs.gridy = 1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        panel.add( wrapCheckbox , cnstrs );

        // add status line
        cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1.0; cnstrs.weightx = 0.33;
        cnstrs.gridheight = 1 ; cnstrs.gridwidth = 1;
        cnstrs.gridx = 0; cnstrs.gridy = 2;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        panel.add( label , cnstrs );

        frame.getContentPane().add( panel );
        frame.setPreferredSize( new Dimension(200,100 ) );
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

        // Read source file.

        // The following code INTENTIONALLY converts all line endings to \n
        // as this is what the Scanner class does as well (see Scanner#isSkipCarriageReturn() default)

        // The reason for this is that Java TextComponents internally always use \n as EOL sequence
        // and we otherwise could not line up text offsets generated by the scanner with text offsets
        // generated by the editor TextPane
        final String source;
        try (var reader = new BufferedReader( new InputStreamReader( unit.getResource().createInputStream(), unit.getResource().getEncoding() ) ) )
        {
            source = reader.lines().collect( Collectors.joining( "\n" ) );
        }

        lastEditLocation = -1;

        autoComplete.detach();
        editor.setDocument( createDocument() );
        autoComplete.attachTo( editor );

        setText(source);
        undoManager.discardAllEdits();
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

    public boolean close(boolean askIfDirty)
    {
        lastEditLocation = -1;
        setVisible( false );
        final Container parent = getParent();
        parent.remove( this );
        parent.revalidate();
        try {
            return true;
        } finally {
            afterRemove();
        }
    }

    protected abstract void afterRemove();

    public IProject getProject() {
        return project;
    }

    public CompilationUnit getCompilationUnit() {
        return currentUnit;
    }

    public void gotoMessage(CompilationMessage message)
    {
        final int len = editor.getText().length();
        if ( message.region != null && 0 <= message.region.start() && message.region.start() < len )
        {
            setSelection( message.region );
        } else {
            System.err.println("Index "+message.region+" is out of range, cannot set caret");
        }
    }

    public void setSelection(TextRegion region)
    {
        final Runnable r = () -> {
            editor.setCaretPosition( region.start() );
            editor.setSelectionStart( region.start() );
            editor.setSelectionEnd( region.end() );
            editor.requestFocus();
        };
        runAfterCompilation(r);
    }

    private void runAfterCompilation(Runnable r)
    {
        if ( wasCompiledAtLeastOnce ) {
            r.run();
        } else {
            afterCompilation.add( r );
        }
    }

    private void setCaretPosition(int position)
    {
        runAfterCompilation( () ->
                             {
                                 ignoreEditEvents = true;
                                 try
                                 {
                                     editor.setCaretPosition( position );
                                 } finally {
                                     ignoreEditEvents = false;
                                 }
                                 editor.requestFocus();
                             } );
    }

    private void setText(String text)
    {
        backDOM.clear();
        frontDOM.clear();

        indentFilterEnabled = false;
        try
        {
            editor.setText(text);
        } finally {
            indentFilterEnabled = true;
        }
    }

    public AST getAST() {
        return astTreeModel.getAST();
    }

    public CompilationUnit currentUnit() {
        return currentUnit;
    }

    public SourceMap getSourceMap() {
        return sourceMap;
    }

    private static String htmlEscape(String input) {
        if ( input == null || input.isBlank() ) {
            return input;
        }
        final StringBuilder buffer = new StringBuilder(input.length());
        for ( char c : input.toCharArray() )
        {
            switch( c ) {
                case '&' -> buffer.append( "&amp;" );
                case '<' -> buffer.append( "&lt;" );
                case '>' -> buffer.append( "&gt;" );
                default -> buffer.append( c );
            }
        }
        return buffer.toString();
    }
}