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

import java.awt.Dimension;
import java.awt.Frame;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.arch.IArchitecture.DisassemblerSettings;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;

public class Main 
{
    private static final String WORKSPACE_FILE = ".javr_workspace";
    
    private static final Logger LOG = Logger.getLogger(Main.class);
    
    private final List<IProject> projects = new ArrayList<>();
    
    private IProject project;
    private EditorFrame editorFrame;
    private File lastOpenedProject = new File("/home/tobi/atmel/asm/testproject.properties");
    private File lastDisassembledFile = new File("/home/tobi/atmel/asm/random.raw");
    private File lastSourceFile = new File("/home/tobi/atmel/asm/random.raw.javr.asm");
    
    public static void main(String[] args) 
    {
        final Runnable runnable = () -> 
        {
            try {
                new Main().run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        SwingUtilities.invokeLater( runnable );
    }

    private static String getWorkingDir() {
        return System.getProperty("user.home" , Project.getCurrentWorkingDirectory().getAbsolutePath() );
    }
    
    private static File getWorkspaceFile() 
    {
        return new File( getWorkingDir() , WORKSPACE_FILE );
    }
    
    private List<IProject> findProjects(File workspaceDir) throws IOException {
        
        final List<IProject>  result = new ArrayList<>();
        final File[] filesInWorkspaceDir = workspaceDir.listFiles();
        if ( filesInWorkspaceDir != null ) 
        {
            for ( File projDirectory : filesInWorkspaceDir ) 
            {
                if ( ! projDirectory.isDirectory() ) {
                    continue;
                }
                final File projFile = new File( projDirectory , IProject.PROJECT_FILE );
                if ( projFile.exists() ) 
                {
                    try ( InputStream in = new FileInputStream(projFile) ) 
                    {
                        final ProjectConfiguration config = ProjectConfiguration.load( projDirectory , in );
                        final File sourceFolder = new File( projDirectory , config.getSourceFolder() );
                        if ( ! sourceFolder.exists() ) {
                            if ( ! sourceFolder.mkdirs() ) {
                                throw new IOException("Failed to create source folder "+sourceFolder.getAbsolutePath());
                            }
                        }
                        result.add( new Project( new CompilationUnit( config.getCompilationRootResource() )  ,config ) );
                    } 
                    catch(Exception e) 
                    {
                        LOG.error("findProjects()",e);
                    }
                }
            }
        }
        return result;
    }
    
    private void run() throws Exception 
    {
        final File projectsFile = getWorkspaceFile();
        File workspaceDir = new File( getWorkingDir() , "javr_workspace");
        
        if ( projectsFile.exists() ) 
        {
            try ( FileInputStream in = new FileInputStream(projectsFile) ) 
            {
                final List<String> lines = IOUtils.readLines( in );
                if ( lines.size() > 0 ) 
                {
                    workspaceDir = new File( lines.get(0) );
                    LOG.info("run(): Workspace dir: "+workspaceDir);
                    projects.addAll( findProjects( workspaceDir ) );
                } 
            }
        } 
        else 
        {
            workspaceDir.mkdirs();
            try ( BufferedWriter writer = new BufferedWriter( new FileWriter( projectsFile ) ) ) 
            {
                writer.write( workspaceDir.getAbsolutePath() );
            }
        }
        
        if ( projects.isEmpty() ) 
        {
            final ProjectConfiguration config = new ProjectConfiguration();
            final File projDir = new File( workspaceDir , config.getProjectName() );
            projDir.mkdirs();
            config.setBaseDir( projDir );
            try ( FileOutputStream out = new FileOutputStream( new File( projDir , IProject.PROJECT_FILE ) ) ) 
            {
                config.save( out );
            }
            project = new Project( new CompilationUnit( config.getCompilationRootResource() ) , config );
            projects.add(project);
        } else {
            project = projects.get(0);
        }
        
        final JDesktopPane pane = new JDesktopPane();
        addWindows( pane );
        
        final JFrame frame = new JFrame();
        frame.setJMenuBar( createMenu(frame) );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        frame.setPreferredSize( new Dimension(640,480));
        frame.setMinimumSize( new Dimension(640,480));
        frame.setContentPane( pane );
        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible( true );
        frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        
        Stream.of( pane.getAllFrames() ).forEach( internalFrame -> 
        {
            try {
                internalFrame.setMaximum(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });        
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
                    config.save( out );
                    project.setConfiguration( config );
                } catch(Exception e) {
                    fail(e);
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
        
        addItem( menu , "Open project" , () -> doWithFile( parent , true , lastOpenedProject, file -> 
        {
            try ( FileInputStream in = new FileInputStream( file ) )
            {
                final ProjectConfiguration config = ProjectConfiguration.load( file.getParentFile() , in );
                lastOpenedProject = file;
                this.project = new Project( new CompilationUnit( new StringResource("unnamed", "" ) ) , config );
            } 
            catch(Exception e) 
            {
                fail(e);
            }
        }));
        
        addItem( menu , "Edit project configuration" , () -> editProjectConfiguration() );        
        
        addItem( menu , "Disassemble" , () -> doWithFile( parent , true , lastDisassembledFile, file -> 
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
            doWithFile( parent , false , srcFolder , file -> 
            {
                editorFrame.save(file); 
                lastSourceFile = file;
            });
        };
        addItem( menu , "Save source" , eventHandler);  
        addItem( menu , "Load source" , () -> doWithFile( parent , true , lastSourceFile, file -> 
        {
            final CompilationUnit unit = new CompilationUnit( new FileResource(file , Resource.ENCODING_UTF ) );
            editorFrame.setProject( project , unit );
            lastSourceFile = file;
        })); 
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
    
    public static void fail(Exception e) 
    {
        LOG.error("fail(): "+e.getMessage(),e);
        
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintWriter writer = new PrintWriter( out );
        e.printStackTrace( writer );
        
        final String body = "Error: "+e.getMessage()+"\n\n"+new String( out.toByteArray() );
        JOptionPane.showMessageDialog(null, body , "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    protected interface ThrowingConsumer<T> {
        public void apply(T obj) throws Exception;
    }
    
    protected interface ThrowingRunnable {
        public void run() throws Exception;
    }    
    
    private void doWithFile(JFrame parent,boolean openDialog,File file , ThrowingConsumer<File> handler) throws Exception 
    {
        final JFileChooser chooser = new JFileChooser();
        if ( file != null ) {
            chooser.setSelectedFile( file );
        }
        final int result = openDialog ? chooser.showOpenDialog( parent ) : chooser.showSaveDialog( parent );
        if ( result == JFileChooser.APPROVE_OPTION ) 
        {
            handler.apply( chooser.getSelectedFile() );
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
                fail(e);
            } 
        });
        menu.add( item );
    }

    private void addWindows(JDesktopPane pane) throws IOException 
    {
        editorFrame = new EditorFrame( project , project.getCompileRoot() );
        editorFrame.setResizable(true);
        editorFrame.setMaximizable(true);
        editorFrame.pack();
        editorFrame.setVisible( true );
        pane.add( editorFrame );
    }
}