package de.codesourcery.javr.ui.panels;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeWillExpandListener;
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
	private Consumer<File> selectionHandler = file -> {}; 
	
	protected static final class DirNode 
	{
		public final File file;
		private final DirNode parent;
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

		@Override
		public String toString() {
			return file.getName();
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
	}

	protected static final class MyTreeModel implements TreeModel {

		private final List<TreeModelListener> listeners = new ArrayList<>();

		private final DirNode root;

		public MyTreeModel(DirNode root) {
			Validate.notNull(root, "root must not be NULL");

			this.root = root;
		}

		public void fetchChildren(DirNode node) 
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
				final File[] contents = node.file.listFiles();
				if ( contents != null) 
				{
					final List<File> dirs = new ArrayList<>( Arrays.asList( contents ) );
					dirs.removeIf( f -> f.getName().startsWith("." ) );
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
			for (TreeModelListener l : listeners ) 
			{
				try {
					l.treeNodesInserted( event );
				} catch(Exception e) {
					LOG.error("TreeModelListener( "+l+" ) failed: "+e,e);
				}
			}
		}

		@Override
		public Object getRoot() {
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
		
		final JScrollPane pane = new JScrollPane( tree );
		setLayout( new GridBagLayout() );
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.weightx=1.0;cnstrs.weighty=1.0f;
		cnstrs.gridx=1;cnstrs.gridy=1;
		cnstrs.fill = GridBagConstraints.BOTH;
		add(pane, cnstrs );

		final TreeWillExpandListener listener = new TreeWillExpandListener() {

			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException 
			{
				final DirNode child = (DirNode) event.getPath().getLastPathComponent();
				if ( ! child.dataFetched ) 
				{
					treeModel.fetchChildren( child );
				}
			}

			@Override
			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
			}
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

			final FileSystemBrowser browser = new FileSystemBrowser( new File("/home/tgierke" ) );
			test.getContentPane().add( browser );
		};
		SwingUtilities.invokeLater(r);
	}

	public void setFolder(File directory) {

		Validate.notNull(directory, "directory must not be NULL");
		final DirNode root = new DirNode( directory , null );
		this.treeModel = new MyTreeModel( root );
		treeModel.fetchChildren( root );
		this.tree.setModel( treeModel );
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
}