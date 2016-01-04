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
package de.codesourcery.javr.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.TableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
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

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.Assembler;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.exceptions.ParseException;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Location;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.ast.AST;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IASTVisitor;
import de.codesourcery.javr.assembler.parser.ast.ASTNode.IIterationContext;
import de.codesourcery.javr.assembler.parser.ast.CommentNode;
import de.codesourcery.javr.assembler.parser.ast.DirectiveNode;
import de.codesourcery.javr.assembler.parser.ast.IdentifierNode;
import de.codesourcery.javr.assembler.parser.ast.InstructionNode;
import de.codesourcery.javr.assembler.parser.ast.LabelNode;
import de.codesourcery.javr.assembler.parser.ast.NumberLiteralNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;
import de.codesourcery.javr.assembler.parser.ast.PreprocessorNode;
import de.codesourcery.javr.assembler.parser.ast.RegisterNode;
import de.codesourcery.javr.assembler.parser.ast.StatementNode;
import de.codesourcery.javr.assembler.util.FileResourceFactory;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;

public class EditorFrame extends JInternalFrame implements IViewComponent {

    private static final Logger LOG = Logger.getLogger(EditorFrame.class);
    
    public static final Duration RECOMPILATION_DELAY = Duration.ofMillis( 150 );
    
    private final JTextPane editor = new JTextPane();
    private final IConfigProvider configProvider;

    private JFrame astWindow = null;

    private final MyTreeModel astTreeModel = new MyTreeModel();
    private final MessageModel messageModel = new MessageModel();
    
    private final RecompilationThread recompilationThread = new RecompilationThread();

    protected final Style STYLE_TOPLEVEL;
    protected final Style STYLE_LABEL;
    protected final Style STYLE_NUMBER;
    protected final Style STYLE_REGISTER;
    protected final Style STYLE_MNEMONIC;
    protected final Style STYLE_COMMENT;

    private CompilationUnit compilationUnit = new CompilationUnit( new StringResource( "dummy","" ) );
    
    private LineMap lineMap;

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
            return in.replace("\t" ,  configProvider.getConfig().getEditorIndentString() );
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

    private final class MessageModel implements TableModel {

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
                    final Location loc = lineMap.getLocationFor( msg.region );
                    return loc == null ? "<unknown>" : loc.toString();
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

    private final class MyTreeModel implements TreeModel 
    {
        private final List<TreeModelListener> listeners = new ArrayList<>();

        private AST ast = new AST();

        public void setAST(AST ast) 
        {
            this.ast = ast;

            final TreeModelEvent ev = new TreeModelEvent(this, new TreePath(this.ast) );
            System.out.println("Tree has "+ast.childCount()+" statements");
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

    public EditorFrame(IConfigProvider provider) 
    {
        super("Editor");
        Validate.notNull(provider, "provider must not be NULL");

        this.configProvider = provider;
        editor.setPreferredSize( new Dimension(200,300 ) );
        
        final JPanel panel = new JPanel();
        panel.setLayout( new BorderLayout() );

        // toolbar
        panel.add( createToolbar() , BorderLayout.NORTH );

        // editor
        panel.add( new JScrollPane( editor ) , BorderLayout.CENTER );

        // error messages table
        final JTable errorTable = new JTable( messageModel );
        errorTable.addMouseListener( new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) 
            {
                if ( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) 
                {
                    int row = errorTable.rowAtPoint( e.getPoint() );
                    if ( row != -1 ) {
                        CompilationMessage msg = messageModel.getRow( row );
                        if ( msg.region != null ) 
                        {
                            editor.setCaretPosition( msg.region.start() );
                            editor.requestFocus();
                        }
                    }
                }
            }
        });
        panel.add( new JScrollPane( errorTable ) , BorderLayout.SOUTH);

        getContentPane().add( panel );

        this.lineMap = new LineMap("",provider);

        // setup styles
        final StyleContext ctx = new StyleContext();

        final DefaultStyledDocument doc = new DefaultStyledDocument(ctx);
        editor.setStyledDocument( doc );

        final Style topLevelStyle = ctx.addStyle( "topLevelStyle" , null);
        STYLE_TOPLEVEL = topLevelStyle;

        STYLE_LABEL    = createStyle( "labelStyle" , Color.GREEN , topLevelStyle , ctx );
        STYLE_NUMBER   = createStyle( "numberStyle" , Color.BLUE , topLevelStyle , ctx );
        STYLE_REGISTER = createStyle( "registerStyle" , Color.MAGENTA, topLevelStyle , ctx );
        STYLE_MNEMONIC = createStyle( "mnemonicStyle" , Color.BLACK , topLevelStyle , ctx );
        STYLE_COMMENT  = createStyle( "commentStyle" , Color.GRAY , topLevelStyle , ctx );     
        
        // setup recompilation
        recompilationThread.start();
        
        editor.getStyledDocument().addDocumentListener( new DocumentListener() 
        {
            @Override public void insertUpdate(DocumentEvent e) {  recompilationThread.documentChanged(); }
            @Override public void removeUpdate(DocumentEvent e) {  recompilationThread.documentChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { recompilationThread.documentChanged(); }
        });
        
        // setup auto-indent
        ((AbstractDocument) editor.getStyledDocument()).setDocumentFilter( new IndentFilter() );
    }

    private JToolBar createToolbar() 
    {
        final JToolBar result = new JToolBar(JToolBar.HORIZONTAL);

        result.add( button("Compile" , ev -> this.compile() ) );
        result.add( button("AST" , ev -> this.toggleASTWindow() ) );

        return result;
    }
    
    private void save() throws IOException 
    {
        String text = editor.getText();
        text = text == null ? "" : text;
        
        final Resource resource = compilationUnit.getResource();
        try ( OutputStream out = resource.createOutputStream() ) 
        {
            out.write( text.getBytes( resource.getEncoding() ) );
        }
    }

    private void compile() 
    {
        messageModel.clear();
        
        try {
            save();
        } 
        catch(IOException e) 
        {
            LOG.error("compile(): Failed to save changes",e);
            messageModel.add( CompilationMessage.error( "Failed to save changes") );
            return;
        }
        
        Parser p = configProvider.getConfig().createParser();
        String text = editor.getText();
        text = text == null ? "" : text;
        
        this.lineMap = new LineMap(text,configProvider);        
        
        // parse
        long parseTime = 0;
        final AST ast;
        try {
           final long start = System.currentTimeMillis();
            ast = p.parse( new Lexer( new Scanner( text ) ) );
            parseTime = System.currentTimeMillis() - start;
            astTreeModel.setAST( ast );
        } 
        catch(Exception e) 
        {
            e.printStackTrace();
            astTreeModel.setAST( new AST() );
            messageModel.add( toCompilationMessage( e ) );
            return;
        }

        doSyntaxHighlighting();
        
        // assemble
        long assembleTime = 0;
        final Assembler asm = new Assembler();
        try {
            final long start = System.currentTimeMillis();
            asm.assemble( compilationUnit , FileResourceFactory.createInstance( new File("/home/tobi/atmel/asm") , "dummy" ) , configProvider );
            assembleTime = System.currentTimeMillis() -start;
        } 
        catch(Exception e) 
        {
            e.printStackTrace();
            ast.addMessage( toCompilationMessage( e ) );
        }
        if ( ast.hasErrors() ) 
        {
            messageModel.addAll( ast.getMessages() );
        } 
        else 
        {
            final long ms = parseTime+assembleTime;
            float seconds = ms/1000f;
            final DecimalFormat DF = new DecimalFormat("#######0.0#");
            float linesPerSeconds = lineMap.getLineCount()/seconds;
            final String time = "Time: "+ ms +" ms , "+DF.format( linesPerSeconds )+" lines/s , parse: "+parseTime+" ms, assemble: "+assembleTime+" ms";
            messageModel.add( new CompilationMessage(Severity.INFO, time) );
            DateTimeFormatter df = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss");
            messageModel.add( new CompilationMessage(Severity.INFO, "Compilation successful ("+ms+" millis) on "+df.format( ZonedDateTime.now() ) ) );
        }          
    }
    
    private static CompilationMessage toCompilationMessage(Exception e)
    {
        if ( e instanceof ParseException ) {
            return new CompilationMessage(Severity.ERROR , e.getMessage() , new TextRegion( ((ParseException ) e).getOffset() , 0 ) ); 
        } 
        return new CompilationMessage(Severity.ERROR , e.getMessage() );
    }

    private void doSyntaxHighlighting() 
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
                    if ( node instanceof RegisterNode) {
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
                    else if ( node instanceof LabelNode ) 
                    {
                        style = STYLE_LABEL;
                    }      
                    else if ( node instanceof NumberLiteralNode ) 
                    {
                        style = STYLE_NUMBER;
                    }                      
                    
                    if ( style != null ) {
                        doc.setCharacterAttributes( region.start(), region.length() , style , true );
                    }
                }
            }
        };
        astTreeModel.getAST().visitBreadthFirst( visitor );
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

                    if ( node instanceof DirectiveNode) {
                        text = "."+((DirectiveNode) node).directive.literal;
                    } 
                    else  if ( node instanceof PreprocessorNode) {
                        text = "#"+((PreprocessorNode) node).type.literal;
                    } else if ( node instanceof OperatorNode) {
                        text = "Operator: "+((OperatorNode) node).type.getSymbol();
                    } else  if ( node instanceof IdentifierNode) {
                        text = "identifier: "+((IdentifierNode) node).value.value;
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

                    setText( text );
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

    private static Style createStyle(String name,Color col,Style parent,StyleContext ctx) {

        final Style style = ctx.addStyle( name , parent );
        style.addAttribute(StyleConstants.Foreground, col );
        return style;
    }    
}
