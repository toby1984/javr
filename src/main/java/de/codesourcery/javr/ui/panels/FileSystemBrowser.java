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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

public class FileSystemBrowser extends JPanel
{
	private static final Logger LOG = Logger.getLogger(FileSystemBrowser.class);

	private MyTreeModel treeModel;

	private final JTree tree = new JTree( treeModel );

	private Predicate<File> fileFilter = f -> true;
	private Function<DirNode,JPopupMenu> menuSupplier = node -> null;
	
	private Consumer<File> selectionHandler = file -> {}; 
	
	private File root;
	
	public static final class DirNode 
	{
		public final File file;
		public final DirNode parent;
		private final List<DirNode> children = new ArrayList<>();
		public boolean dataFetched = false;

		public DirNode(File file,DirNode parent) {
			Validate.notNull(file, "file must not be NULL");
			this.file = file;
			this.parent = parent;
		}

		public List<DirNode> children() {
			return children;
		}
		
		public void remove() {
		    if ( parent == null ) {
		        throw new UnsupportedOperationException("Cannot unlink tree node that has no parent");
		    } 
		    parent.removeChild( this );
		}
		
		public void removeChild(DirNode child) 
		{
		    for (Iterator<DirNode> iterator = children.iterator(); iterator.hasNext();) 
		    {
                final DirNode n = iterator.next();
                if ( n == child ) {
		            iterator.remove();
		            return;
		        }
            }
		    throw new IllegalArgumentException("Failed to remove child "+child+" from parent "+this+" -- not found");
		}

		@Override
		public String toString() {
			return file.getAbsolutePath();
		}
		
		public DirNode findClosestNode(File toFind) 
		{
		    final String absPath = toFind.getAbsolutePath();
		    if ( absPath.equals( this.file.getAbsolutePath() ) ) {
		        return this; // perfect match
		    }
		    if ( ! absPath.startsWith( this.file.getAbsolutePath() ) ) {
		        return null;
		    }
		    DirNode longestMatch = null;
		    int longestMatchPrefix = 0;
		    
		    for ( DirNode child : children) 
		    {
		        int prefixLen = child.getMatchingPathPrefixLen( absPath );
		        if ( prefixLen > 0 && ( longestMatch == null || prefixLen > longestMatchPrefix ) ) {
		            longestMatch = child;
		            longestMatchPrefix = prefixLen;
		        }
		    }
		    if ( longestMatch != null ) {
		        DirNode result = longestMatch.findClosestNode( toFind );
		        if ( result != null ) 
		        {
		            if ( ! result.file.getAbsolutePath().equals( absPath ) ) // we found something but it's not a full match
		            {
		                if ( ! result.file.isDirectory() ) // we failed to find a full match so return the directory where we found the match instead
		                {
		                    return result.parent;
		                }
		            }
		            return result;
		        }
		        return longestMatch;
		    }
		    return null;
		}
		
		private int getMatchingPathPrefixLen(String path) 
		{
		    final String thisPath = this.file.getAbsolutePath();
		    final int len = Math.min( path.length() , thisPath.length());
		    int i = 0;
		    for ( ; i < len ; i++ ) 
		    {
		        if ( path.charAt(i) != thisPath.charAt(i) ) {
		            break;
		        }
		    }
		    return i;
		} 
		
		public List<DirNode> getPathToRoot() 
		{
			final List<DirNode> pathToParent = new ArrayList<>();
			DirNode current = this;
			do 
			{
				pathToParent.add( current );
				current = current.parent;
			} while ( current != null );
			Collections.reverse( pathToParent );
			return pathToParent;
		}
		
		public TreePath getTreePathToRoot() {
		    
		    return new TreePath( getPathToRoot().toArray( new DirNode[0] ) );
		}
	}

	protected final class MyTreeModel implements TreeModel {

		private final List<TreeModelListener> listeners = new ArrayList<>();

		private DirNode root;

		public MyTreeModel(File root) {
			setRoot( root );
		}
		
		public void setRoot(File file) {
		    Validate.notNull(file, "root must not be NULL");
            setRoot( new DirNode( file , null ) );
		}
		
		private void setRoot(DirNode root) {
		    this.root = root;
		    final TreeModelEvent event = new TreeModelEvent(this,new Object[]{ root } );
            notifyListeners( l -> l.treeStructureChanged( event ) );
            reload();
		}
		
		public void subtreeChanged(DirNode node) 
		{
            node.dataFetched = false;
            node.children.clear();
            getFiles( node.file ).forEach( file -> 
            {
                node.children.add( new DirNode( file , node ) );
            });
            node.dataFetched = true;
            
            final List<DirNode> nodes = node.getPathToRoot(); // element[0] = root , ...
            final TreePath path = new TreePath( nodes.toArray() );
            
            final TreeModelEvent ev = new TreeModelEvent( this , path );
            notifyListeners( l -> l.treeStructureChanged( ev ) );
		}

		private List<File> getFiles(File file) 
		{
            final File[] contents = file.listFiles();
            if ( contents != null) 
            {
                final List<File> dirs = new ArrayList<>( Arrays.asList( contents ) );
                dirs.removeIf( f -> f.getName().startsWith("." ) || ! fileFilter.test( f ) );
                dirs.sort( (a,b) -> 
                {
                    if ( a.isDirectory() && b.isDirectory() || a.isFile() && b .isFile() ) 
                    {
                        return a.getName().compareTo( b.getName() );
                    } 
                    if ( a .isDirectory() ) {
                        return -1;
                    }
                    return 1;
                });
                return dirs;
            }
            return new ArrayList<>();
		}
		
		public void reload() {
		    fetchChildren( getRoot() );
		}
		
		private void fetchChildren(DirNode node) 
		{
			if (node.dataFetched ) {
				return;
			}

			final List<Integer> childIndices = new ArrayList<>();
			final List<DirNode> childNodes = new ArrayList<>();
			int i = node.children().size();
			try 
			{
				System.out.println("Fetching children of "+node.file.getAbsolutePath());
				final List<File> dirs = getFiles( node.file );
				for ( File f : dirs ) 
				{
					System.out.println("Got "+f.getAbsolutePath());
					childIndices.add( i++ );
					final DirNode newChild = new DirNode( f , node );
					childNodes.add( newChild );
					node.children.add( newChild  );
					i++;
				}
			} 
			finally 
			{
				node.dataFetched = true;
			}

			final List<DirNode> pathToParent = node.getPathToRoot();

			final TreeModelEvent event = new TreeModelEvent( this , 
					pathToParent.toArray( new DirNode[0] ) , 
					childIndices.stream().mapToInt( x -> x.intValue() ).toArray(),
					childNodes.toArray( new DirNode[0] ) 
					);
			
			notifyListeners( l -> l.treeNodesInserted( event ) );
		}
		
		private void notifyListeners(Consumer<TreeModelListener> c ) 
		{
	          for (TreeModelListener l : listeners ) 
	            {
	                try {
	                    c.accept( l );
	                } catch(Exception e) {
	                    LOG.error("notifyListeners(): TreeModelListener( "+l+" ) failed: "+e,e);
	                }
	            }
		}
		

		@Override
		public DirNode getRoot() {
			return root;
		}

		@Override
		public Object getChild(Object parent, int index) 
		{
			return ((DirNode) parent).children.get( index );
		}

		@Override
		public int getChildCount(Object parent) {
			return ((DirNode) parent).children.size();
		}

		@Override
		public boolean isLeaf(Object node) 
		{
			final DirNode dirNode = (DirNode) node;
			if ( dirNode.file.isDirectory() && ! dirNode.dataFetched ) {
				fetchChildren( dirNode );
			}
			return dirNode.file.isFile() || dirNode.children.isEmpty();
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
			throw new UnsupportedOperationException("valueForPathChanged not implemented yet");
		}

		@Override
		public int getIndexOfChild(Object parent, Object child) {
			return ((DirNode) parent).children.indexOf( child );
		}

		@Override
		public void addTreeModelListener(TreeModelListener l) {

			Validate.notNull(l, "l must not be NULL");
			this.listeners.add(l);
		}

		@Override
		public void removeTreeModelListener(TreeModelListener l) {
			Validate.notNull(l, "l must not be NULL");
			this.listeners.remove(l);
		}
	}

	public FileSystemBrowser(File startingDirectory) 
	{
		setFolder(startingDirectory);

		tree.setRootVisible(false);
		
		tree.setCellRenderer( new DefaultTreeCellRenderer() 
		{
		    @Override
		    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) 
		    {
		        Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
		        if ( value instanceof DirNode ) 
		        {
		            setText( ((DirNode) value).file.getName() );
		        }
		        return result;
		    }
		    
		});
		tree.addMouseListener( new MouseAdapter() 
		{
			@Override
			public void mouseClicked(MouseEvent e) 
			{
				if ( e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton( e ) ) 
				{
					final TreePath path = tree.getClosestPathForLocation( e.getX() , e.getY() );
					if ( path != null ) 
					{
						final DirNode dirNode = (DirNode) path.getLastPathComponent();
						TreePath selection = tree.getSelectionPath();
						if ( dirNode.file.isFile() && selection != null && selection.getLastPathComponent() == dirNode) 
						{
							final File selectedFile = dirNode.file;
							System.out.println("Clicked: "+selectedFile);
							selectionHandler.accept( selectedFile );
						}
					}
				}
			}
		});
		tree.addMouseListener( new PopupListener() );
		
		final JScrollPane pane = new JScrollPane( tree );
		setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.weightx=1.0;cnstrs.weighty=1.0f;
		cnstrs.gridx=1;cnstrs.gridy=1;
		cnstrs.fill = GridBagConstraints.BOTH;
		
		add(pane, cnstrs );

		final TreeWillExpandListener listener = new TreeWillExpandListener() 
		{
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException 
			{
				final DirNode child = (DirNode) event.getPath().getLastPathComponent();
				if ( ! child.dataFetched ) 
				{
					treeModel.fetchChildren( child );
				}
			}

			@Override public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {  }
		};
		tree.addTreeWillExpandListener( listener );
	}

	public static void main(String[] args) 
	{
		final Runnable r = () -> 
		{
			final JFrame test = new JFrame();
			test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			test.setMinimumSize( new Dimension(50, 50) );
			test.setPreferredSize( new Dimension(200, 200) );
			test.pack();
			test.setLocationRelativeTo( null );
			test.setVisible( true );

			final FileSystemBrowser browser = new FileSystemBrowser( new File("/home/tobi" ) );
			test.getContentPane().add( browser );
		};
		SwingUtilities.invokeLater(r);
	}

	public void setFolder(File directory) {

		Validate.notNull(directory, "directory must not be NULL");
		this.root = directory;
		this.treeModel = new MyTreeModel( directory );
		this.tree.setModel( treeModel );
	}
	
	public File getRoot() {
		return this.root;
	}
	
	public void setFileFilter(Predicate<File> fileFilter) 
	{
		Validate.notNull(fileFilter, "fileFilter must not be NULL");
		this.fileFilter = fileFilter;
	}
	
	public void setSelectionHandler(Consumer<File> selectionHandler) {
		Validate.notNull(selectionHandler, "selectionHandler must not be NULL");
		this.selectionHandler = selectionHandler;
	}
	
    private class PopupListener extends MouseAdapter 
    {
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) 
        {
            if (e.isPopupTrigger()) 
            {
                final TreePath path = tree.getClosestPathForLocation( e.getX() , e.getY() );
                final DirNode node = (DirNode) ( path != null && path.getPathCount() > 0 ? path.getLastPathComponent() : null );
                final JPopupMenu popup = menuSupplier.apply( node );
                if ( popup != null ) 
                {
                    popup.show(e.getComponent(),e.getX(), e.getY());
                }
            }
        }
    }
    
    public void setMenuSupplier(Function<DirNode, JPopupMenu> menuSupplier) 
    {
        Validate.notNull(menuSupplier, "menuSupplier must not be NULL");
        this.menuSupplier = menuSupplier;
    }
    
    public void fileRemoved(File file) 
    {
        final DirNode current = treeModel.getRoot().findClosestNode( file );
        if ( current != null ) 
        {
            if ( current.file.equals( file ) ) 
            {
                final int idx = treeModel.getIndexOfChild(  current.parent , current );
                final TreePath path = current.parent.getTreePathToRoot();
                
                current.parent.children.remove( current );
                
                final TreeModelEvent ev = new TreeModelEvent( this , path , new int[] {idx} , new DirNode[] { current } );
                treeModel.notifyListeners( l -> l.treeNodesRemoved( ev ) );
            } 
            else 
            {
                pathChanged( current.file );
            }
        }
    }

    public void pathChanged(File topLevelDir) 
    {
        final DirNode current = treeModel.getRoot().findClosestNode( topLevelDir );
        if ( current != null ) 
        {
            treeModel.subtreeChanged( current );
        }
    }
}