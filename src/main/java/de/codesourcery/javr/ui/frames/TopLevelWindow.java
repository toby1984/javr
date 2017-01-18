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

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.arch.IArchitecture.DisassemblerSettings;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;
import de.codesourcery.javr.ui.CaretPositionTracker;
import de.codesourcery.javr.ui.IDEMain;
import de.codesourcery.javr.ui.IDEMain.ThrowingConsumer;
import de.codesourcery.javr.ui.IDEMain.ThrowingFunction;
import de.codesourcery.javr.ui.IDEMain.ThrowingRunnable;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.Project;
import de.codesourcery.javr.ui.ProjectConfigWindow;
import de.codesourcery.javr.ui.config.IApplicationConfig;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.panels.EditorPanel;
import de.codesourcery.javr.ui.panels.FileSystemBrowser.DirNode;

public class TopLevelWindow implements IWindow
{
    private static final Logger LOG = Logger.getLogger(TopLevelWindow.class);
    
    private IApplicationConfigProvider applicationConfigProvider;
    private IProject project;
    
    private NavigatorFrame navigator;
    private EditorFrame editorFrame;
    
    private final JDesktopPane desktopPane = new JDesktopPane();
    private final JFrame topLevelFrame = new JFrame();
    private final MessageFrame messageFrame = new MessageFrame("Messages");
    private final OutlineFrame outlineFrame = new OutlineFrame();
    
    private File lastOpenedProject = new File("/home/tobi/atmel/asm/testproject.properties");
    private File lastDisassembledFile = new File("/home/tobi/atmel/asm/random.raw");
    private File lastSourceFile = new File("/home/tobi/atmel/asm/random.raw.javr.asm");
    
    private final CaretPositionTracker caretTracker = new CaretPositionTracker();
    
    public TopLevelWindow(IProject project,IApplicationConfigProvider applicationConfigProvider) throws IOException 
    {
        Validate.notNull(applicationConfigProvider, "applicationConfigProvider must not be NULL");
        this.applicationConfigProvider = applicationConfigProvider;
        this.project = project;
        
        addWindows( desktopPane );
        
        outlineFrame.setDoubleClickListener( symbol -> 
        {
        	try 
        	{
				final EditorPanel editor = editorFrame.openEditor( project , symbol.getCompilationUnit() );
				editor.setSelection( symbol.getTextRegion() );
			} catch (IOException e1) {
				LOG.error("Failed to open compilation unit for symbol "+symbol,e1);
			}
        });
        
        topLevelFrame.setJMenuBar( createMenu(topLevelFrame) );
        topLevelFrame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
        
        topLevelFrame.addWindowListener( new WindowAdapter() 
        {
            @Override
            public void windowClosing(WindowEvent e) 
            {
                quit();
            }
        });

        topLevelFrame.setPreferredSize( new Dimension(640,480));
        topLevelFrame.setMinimumSize( new Dimension(640,480));
        topLevelFrame.setContentPane( desktopPane );
        topLevelFrame.pack();
        topLevelFrame.setLocationRelativeTo( null );
        topLevelFrame.setVisible( true );
        topLevelFrame.setExtendedState(topLevelFrame.getExtendedState() | JFrame.MAXIMIZED_BOTH);

        applicationConfigProvider.getApplicationConfig().apply( this );
        applicationConfigProvider.getApplicationConfig().apply( editorFrame );
        applicationConfigProvider.getApplicationConfig().apply( messageFrame );
        applicationConfigProvider.getApplicationConfig().apply( navigator );           
        applicationConfigProvider.getApplicationConfig().apply( outlineFrame );        
        
        project.addProjectChangeListener( outlineFrame );
        outlineFrame.setCompilationUnit( project.getCompileRoot() );
    }
    
    public void quit() 
    {
        LOG.info("quit(): Shutting down...");
        try 
        {
            final IApplicationConfig config = applicationConfigProvider.getApplicationConfig();
            config.save( editorFrame );
            config.save( navigator );
            config.save( messageFrame );
            config.save( outlineFrame);
            config.save( TopLevelWindow.this );
            applicationConfigProvider.setApplicationConfig( config );
            try {
                IDEMain.getInstance().save( config );
            } catch (IOException e1) {
                LOG.error("windowClosing(): Failed to save configuration",e1);
            } 
        } finally {
            System.exit(0);
        }        
    }
    
    private void addMenuItem(JPopupMenu menu,String label,Runnable action) {
        final JMenuItem item1 = new JMenuItem( label );
        item1.addActionListener( ev -> action.run() );
        menu.add( item1 );
    }
    
    public void openFile(File file)
    {
        try 
        {
            final CompilationUnit unit = project.getCompilationUnit( new FileResource( file , Resource.ENCODING_UTF ) );
            editorFrame.openEditor( project , unit );
        } 
        catch (IOException e) 
        {
            LOG.error("openFile(): Failed to open "+file.getAbsolutePath());
            IDEMain.fail( e );
        }
    }
    
    private void addItem(JMenu menu,String label,ThrowingRunnable eventHandler) 
    {
        final JMenuItem item = new JMenuItem( label );
        item.addActionListener( ev -> 
        {
            try {
                eventHandler.run();
            } catch (Exception e) {
                IDEMain.fail(e);
            } 
        });
        menu.add( item );
    }

    private void addWindows(JDesktopPane pane) throws IOException 
    {
        editorFrame = new EditorFrame( project , project.getCompileRoot() , applicationConfigProvider, messageFrame , caretTracker );
        addWindow(pane,messageFrame);
        addWindow(pane,editorFrame);
        addWindow(pane,outlineFrame);
        navigator = new NavigatorFrame( project.getConfiguration().getBaseDir() );
        
        navigator.setMenuSupplier( dirNode -> 
        {
            if ( dirNode != null ) 
            {
                final JPopupMenu menu = new JPopupMenu();
                addMenuItem(menu,"New directory" , () -> 
                {
                    final DirNode parentNode = dirNode.file.isDirectory() ? dirNode :dirNode.parent;
                    final File parent = parentNode.file;
                    
                    int index = 0;
                    File newFile = new File( parent , "new_directory" );
                    while ( newFile.exists() ) 
                    {
                        index++;
                        newFile = new File( parent , "new_directory_"+index );
                    }
                    if ( ! newFile.exists() ) 
                    {
                        if ( newFile.mkdirs() ) 
                        {
                            navigator.refreshPath( parentNode.file );
                        }
                    }
                });
                addMenuItem(menu,"Delete" , () -> 
                {
                    final File toDelete = dirNode.file;
                	final List<File> files = new ArrayList<>();                    
                    try 
                    {
						if ( toDelete.isDirectory() ) 
						{
							Files.list( toDelete.toPath() ).filter( p -> Files.isRegularFile( p ) ).forEach( p -> files.add( p.toFile() ) );
                            FileUtils.deleteDirectory( toDelete );
                        } else {
                            if ( ! toDelete.delete() ) {
                                throw new IOException("Failed to delete "+toDelete.getAbsolutePath());
                            }
                            files.add( toDelete );
                        }
                    } catch (Exception e) {
                        LOG.error("delete(): Failed to delete "+toDelete.getAbsolutePath(),e);
                        IDEMain.fail(e);
                        return;
                    }          
					try 
					{
						for ( File f : files ) 
						{
							final Resource resource = Resource.file( f );
							final Optional<CompilationUnit> existing = project.maybeGetCompilationUnit( resource );
							existing.ifPresent( unit -> 
							{
								if ( editorFrame.closeEditor( unit, true ) ) 
								{
									existing.ifPresent( project::removeCompilationUnit );
								} 
							});
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
                    navigator.fileRemoved( toDelete );
                });
                addMenuItem(menu,"Open" , () -> 
                {
                    if ( dirNode.file.isFile() ) 
                    {
                        openFile( dirNode.file );
                    }
                });
                addMenuItem(menu,"New source file" , () -> 
                {
                    
                    final DirNode parentNode = dirNode.file.isDirectory() ? dirNode :dirNode.parent;
                    final File parent = parentNode.file;
                    
                    int index = 0;
                    File newFile = new File( parent , "new_file.asm" );
                    while ( newFile.exists() ) 
                    {
                        index++;
                        newFile = new File( parent , "new_file_"+index+".asm" );
                    }
                    try 
                    {
                        if ( ! newFile.createNewFile() ) {
                            throw new IOException("Failed to create "+newFile.getAbsolutePath());
                        }
                        openFile( newFile );
                        navigator.refreshPath();
                    }
                    catch (Exception e) {
                        LOG.error("newFile(): Failed to create file "+newFile.getAbsolutePath(),e);
                        IDEMain.fail(e);
                    }
                });                
                return menu;
            }
            return null;
        });

        navigator.setSelectionHandler( file -> 
        {
            if ( file.isFile() ) {
                openFile( file );
            }
        });
        
        addWindow( pane,navigator );
    }    
    
    private void addWindow(JDesktopPane pane, JInternalFrame frame) 
    {
        frame.setResizable(true);
        frame.setMaximizable(true);
        frame.pack();
        frame.setVisible( true );
        pane.add( frame );
    }
    
    private void editProjectConfiguration() 
    {
        final JDialog dialog = new JDialog( (Frame) null, "Edit project configuration", true );
        dialog.getContentPane().add( new ProjectConfigWindow( project.getConfiguration() ) 
        {
            @Override
            protected void onSave(ProjectConfiguration config) 
            {
                final File configFile = new File( project.getConfiguration().getBaseDir() , IProject.PROJECT_FILE );
                LOG.info("editProjectConfiguration(): Saving configuration to "+configFile.getAbsolutePath());
                try ( FileOutputStream out = new FileOutputStream( configFile ) ) 
                {
                	project.setConfiguration( config );
                    config.save( out );
                } catch(Exception e) {
                    IDEMain.fail(e);
                }
                dialog.dispose();
                editorFrame.compile();
            }

            @Override
            protected void onCancel() {
                dialog.dispose();
            }
        } );

        dialog.pack();
        dialog.setVisible( true );
    }

    private JMenuBar createMenu(JFrame parent) 
    {
        final JMenuBar result = new JMenuBar();

        final JMenu menu = new JMenu("File");
        result.add( menu );

        addItem( menu , "Open project" , () -> doWithFile( "Open project" , true , lastOpenedProject, file -> 
        {
            final File realFile;
            if ( file.isDirectory() ) 
            {
                realFile = new File( file , IProject.PROJECT_FILE );
            } else {
                IDEMain.fail("You need to select a project directory");
                return;
            }
            try ( FileInputStream in = new FileInputStream( file ) )
            {
                LOG.info("Opening project configuration "+file.getAbsolutePath());
                final ProjectConfiguration config = ProjectConfiguration.load( file.getParentFile() , in );
                lastOpenedProject = file;
                this.project = new Project( new CompilationUnit( new StringResource("unnamed", "" ) ) , config );
            } 
            catch(Exception e) 
            {
                IDEMain.fail("Failed ro read project configuration from "+realFile.getAbsolutePath(),e);
            }
        }));

        addItem( menu , "Edit project configuration" , () -> editProjectConfiguration() );        

        addItem( menu , "Disassemble" , () -> doWithFile( "Select raw binary to disassemble" , true , lastDisassembledFile, file -> 
        {
            disassemble(file);
            lastDisassembledFile = file;
        }));

        final ThrowingRunnable eventHandler = () -> 
        {
            final File srcFolder = new File( project.getConfiguration().getSourceFolder() ); 
            if ( ! srcFolder.exists() ) 
            {
                srcFolder.mkdirs();
            }
            doWithFile( "Save source as..." , false , srcFolder , file -> 
            {
                editorFrame.save(file); 
                lastSourceFile = file;
            });
        };
        addItem( menu , "Save source" , eventHandler);  
        addItem( menu , "Load source" , () -> doWithFile( "Load source" , true , lastSourceFile, file -> 
        {
            final CompilationUnit unit = project.getCompilationUnit( new FileResource(file , Resource.ENCODING_UTF ) );
            editorFrame.setProject( project , unit );
            lastSourceFile = file;
        })); 
        addItem( menu , "Quit" , this::quit );
        return result;
    }

    private void disassemble(File file) throws IOException 
    {
        final byte[] data = FileUtils.readFileToByteArray( file );
        System.out.println("Disassembling "+data.length+" bytes");
        final DisassemblerSettings settings = new DisassemblerSettings();
        settings.printBytes = true;
        settings.printAddresses = true;
        settings.resolveRelativeAddresses = true;
        settings.printCompoundRegistersAsLower=false;
        String disassembly = project.getArchitecture().disassemble( data , data.length , settings );
        disassembly = "; disassembled "+data.length+" bytes from "+file.getAbsolutePath()+"\n"+disassembly;

        final StringResource res = new StringResource(file.getAbsolutePath(), disassembly );
        final CompilationUnit unit = new CompilationUnit(res);
        editorFrame.setProject( project , unit );        
    }
    
    public static void doWithFile(String title,boolean openDialog,File file , ThrowingConsumer<File> handler) throws Exception  
    {   
        doWithFile(title,openDialog,file,new ThrowingFunction<File,Void>() {

            @Override
            public Void apply(File obj) throws Exception {
                handler.apply( obj );
                return null;
            }
        });
    }

    public static <T> T doWithFile(String title,boolean openDialog,File file , ThrowingFunction<File,T> handler) throws Exception 
    {
        final JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle( title );
        if ( file != null ) {
            chooser.setSelectedFile( file );
        }
        chooser.setFileSelectionMode( JFileChooser.FILES_AND_DIRECTORIES );
        final int result = openDialog ? chooser.showOpenDialog( null ) : chooser.showSaveDialog( null );
        if ( result == JFileChooser.APPROVE_OPTION ) 
        {
            return handler.apply( chooser.getSelectedFile() );
        }
        return null;
    }

    @Override
    public String getWindowId() 
    {
        return "toplevelwindow";
    }

    @Override
    public Point getLocation() {
        return topLevelFrame.getLocation();
    }

    @Override
    public void setLocation(Point p) {
        topLevelFrame.setLocation( p );
    }

    @Override
    public Dimension getSize() {
        return topLevelFrame.getSize();
    }

    @Override
    public void setSize(Dimension size) {
        topLevelFrame.setSize( size );
    }    
}
