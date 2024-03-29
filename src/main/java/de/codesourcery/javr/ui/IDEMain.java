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
package de.codesourcery.javr.ui;

import java.awt.Frame;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;
import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.ApplicationConfigProvider;
import de.codesourcery.javr.ui.config.IApplicationConfig;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import de.codesourcery.javr.ui.config.IModel;
import de.codesourcery.javr.ui.config.Model;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.frames.SelectProjectDialog;
import de.codesourcery.javr.ui.frames.TopLevelWindow;

public class IDEMain 
{
    private static final String WORKSPACE_FILE = ".javr_workspace";

    private static final Logger LOG = Logger.getLogger(IDEMain.class);

    private static final IDEMain INSTANCE = new IDEMain();

    public interface ThrowingConsumer<T> {
        public void apply(T obj) throws Exception;
    }

    public interface ThrowingFunction<A,B> {
        public B apply(A obj) throws Exception;
    }    

    public interface ThrowingRunnable {
        public void run() throws Exception;
    }  

    private final List<IProject> projects = new ArrayList<>();

    private File workspaceDir;
    private IApplicationConfigProvider applicationConfigProvider;

    private final IProjectProvider projectProvider = new IProjectProvider() {

        private volatile IProject currentProject;        
        private final List<IProjectListener> listeners = new ArrayList<>();
        
        @Override
        public void setProject(IProject newProject)
        {
            Validate.notNull(newProject, "project must not be NULL");
            final IProject oldProject = currentProject;
            final boolean projectChanged = oldProject != newProject;
            if ( projectChanged ) 
            {
                currentProject = newProject;
                if ( projectChanged ) 
                {
                    listeners.forEach( l -> 
                    {
                        System.out.println("project-closed: calling "+l);
                        SwingUtilities.invokeLater( () -> l.projectClosed( oldProject ) );
                    });                        
                }
                listeners.forEach( l -> 
                {
                    System.out.println("project-opened: calling "+l);
                    SwingUtilities.invokeLater( () -> l.projectOpened( newProject ) );
                });
            }
        }

        @Override
        public IProject getProject()
        {
            return currentProject;
        }

        @Override
        public void addProjectListener(IProjectListener l)
        {
            Validate.notNull(l, "listener must not be NULL");
            synchronized(listeners) 
            {
                listeners.add( l );
            }
        }

        @Override
        public void removeProjectCloseListener(IProjectListener l)
        {
            Validate.notNull(l, "listener must not be NULL");
            synchronized(listeners) 
            {
                listeners.remove( l );
            }                
        }

        @Override
        public File getWorkspaceDir() {
            return workspaceDir;
        }
    };
    
    public static IDEMain getInstance() {
        return INSTANCE;
    }

    public static void main(String[] args) 
    {
        final Runnable runnable = () -> 
        {
            try {
                INSTANCE.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        SwingUtilities.invokeLater( runnable );
    }

    private String getWorkingDir() {
        return System.getProperty("user.home" , Project.getCurrentWorkingDirectory().getAbsolutePath() );
    }

    private File getWorkspaceFile() 
    {
        return new File( getWorkingDir() , WORKSPACE_FILE );
    }

    public void save(IApplicationConfig config) throws UnsupportedEncodingException, FileNotFoundException, IOException 
    {
        final File configFile = new File( workspaceDir , ".javrconfig.json");
        LOG.info("save(): Saving configuration to "+configFile.getAbsolutePath());
        ApplicationConfigProvider.save( config , new FileOutputStream( configFile ) );
    }

    private IApplicationConfigProvider getApplicationConfigProvider() 
    {
        final File configFile = new File( workspaceDir , ".javrconfig.json");
        final IApplicationConfigProvider result = new ApplicationConfigProvider();

        boolean gotConfigFromFile = false;
        if ( configFile.exists() ) 
        {
            try 
            {
                final IApplicationConfig loaded = ApplicationConfigProvider.load( new FileInputStream( configFile ) );
                result.setApplicationConfig( loaded );
                gotConfigFromFile = true;
            } 
            catch (IOException e) 
            {
                LOG.error("getApplicationConfigProvider(): Failed to load config from "+configFile.getAbsolutePath()+", using defaults",e);
            }
        }

        if ( ! gotConfigFromFile ) 
        {
            LOG.info("getApplicationConfigProvider(): Configuration inaccessible or missing, trying to save defaults to "+configFile.getAbsolutePath());
            try {
                ApplicationConfigProvider.save( result.getApplicationConfig() , new FileOutputStream( configFile ) );
            } catch (IOException e) {
                LOG.error("getApplicationConfigProvider(): Failed to save config to "+configFile.getAbsolutePath(),e);
            }	        
        }
        return result;
    }

    private static List<IProject> findProjects(File workspaceDir) throws IOException {

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

    private File getWorkspaceDir() throws IOException 
    {
        final File workspaceFile = getWorkspaceFile();
        File workspaceDir = null;
        if ( workspaceFile.exists() ) 
        {
            try ( FileInputStream in = new FileInputStream(workspaceFile) ) 
            {
                final List<String> lines = IOUtils.readLines( in );
                if ( lines.size() == 1 ) 
                {
                    workspaceDir = new File( lines.get(0) );
                } 
                else if ( lines.size() > 1 ) 
                {
                    do 
                    {
                        String choice = (String) JOptionPane.showInputDialog(null, "Choose workspace", "Choose workspace", JOptionPane.QUESTION_MESSAGE, null, 
                                lines.toArray(new String[0] ) , lines.get(0) );
                        final File selection = choice == null ? null : new File(choice);
                        if ( choice != null && selection.exists() && selection.isDirectory() ) 
                        {
                            workspaceDir = selection;
                        }
                    } 
                    while ( workspaceDir == null );
                }
            }
        } 

        if ( workspaceDir == null ) 
        {
            do 
            {
                try {
                    workspaceDir = TopLevelWindow.doWithFile( "Choose workspace directory" , true , new File( getWorkingDir() ) , file -> file );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if ( workspaceDir == null ) 
                {
                    System.err.println("Aborting, no workspace directory selected by user");
                    System.exit(1);
                }
            } while( workspaceDir == null );

            try ( BufferedWriter writer = new BufferedWriter( new FileWriter( workspaceFile ) ) ) 
            {
                writer.write( workspaceDir.getAbsolutePath() );
            }
        }
        LOG.info("run(): Workspace dir: "+workspaceDir);
        return workspaceDir;        
    }
    
    private IProject createNewProject(String projectName, File workspaceDir) throws IOException
    {
        final ProjectConfiguration config = new ProjectConfiguration();
        if ( projectName != null && ! projectName.isBlank() )
        {
            config.setProjectName( projectName );
        }
        config.setUploadCommand("/usr/bin/avrdude -F -V -c stk500v2 -p ATMEGA328P -P /dev/ttyACM1 -b 115200 -U flash:w:%f");
        final File projDir = new File( workspaceDir , config.getProjectName() );
        if ( ! projDir.mkdirs() ) {
            throw new IOException("Failed to create folder "+projDir.getAbsolutePath());
        }
        config.setBaseDir( projDir );

        int i = 1;
        final String name = config.getProjectName();
        while (true ) 
        {
            if ( projects.stream().map( p -> p.getConfiguration().getProjectName() ).anyMatch( s -> s.equals( config.getProjectName() ) ) ) 
            {
                config.setProjectName( name+"_"+i);
                i++;
            } else {
                break;
            }
        }
        try ( FileOutputStream out = new FileOutputStream( new File( projDir , IProject.PROJECT_FILE ) ) ) 
        {
            config.save( out );
        }

        final IProject project = new Project( new CompilationUnit( config.getCompilationRootResource() ) , config );
        assertCompilationRootExists(project);
        return project;
    }

    private static void assertCompilationRootExists(IProject project) throws IOException 
    {
        final ProjectConfiguration config = project.getConfiguration();
        final Resource file = config.getSourceFile( config.getCompilationRoot() );
        if ( ! file.exists() ) 
        {
            try ( BufferedWriter writer = new BufferedWriter( new OutputStreamWriter(file.createOutputStream() ) ) ) {
                writer.write("\n");
            }
        }		
    }

    private void run() throws Exception 
    {
        workspaceDir = getWorkspaceDir();

        applicationConfigProvider = getApplicationConfigProvider();

        projects.addAll( findProjects( workspaceDir ) );

        final Optional<String> optDefaultProject =
            applicationConfigProvider.getApplicationConfig().getGlobalSettings().getDefaultProjectToOpen();

        final IProject currentProject = optDefaultProject
                                        .flatMap( name -> projects.stream().filter( p -> p.getConfiguration().getProjectName().equals(name ) ).findFirst() )
                                        .orElseGet( () -> selectProjectToOpen( workspaceDir ) );

        final String defaultProject = applicationConfigProvider.getApplicationConfig().getGlobalSettings().getDefaultProjectToOpen().orElse( null );

        boolean saveConfig = false;
        if ( ! Objects.equals( defaultProject, currentProject.getConfiguration().getProjectName() ) )
        {
            final IApplicationConfig config = applicationConfigProvider.getApplicationConfig();
            config.updateGlobalSettings( x -> x.setDefaultProjectToOpen( currentProject.getConfiguration().getProjectName() ) );
            applicationConfigProvider.setApplicationConfig( config );
            saveConfig = true;
        }
        LOG.info("run(): Opening project "+currentProject.getConfiguration().getBaseDir().getAbsolutePath());
        assertCompilationRootExists( currentProject ); // required so that editor can display something to the user

        // FIXME: Setting the project here before anybody had a chance to register themselves
        // FIXME: as a IProjectListener is obviously a problem....
        projectProvider.setProject( currentProject );

        final TopLevelWindow topLevelWindow = new TopLevelWindow( projectProvider, applicationConfigProvider );
        projectProvider.setProject( currentProject );

        if ( saveConfig ) {
            topLevelWindow.saveConfig();
        }
    }

    public IProject selectProjectToOpen(File workspaceDir)
    {
        if ( projects.isEmpty() ) 
        {
            final IProject project;
            try
            {
                project = createNewProject(null, workspaceDir);
            } catch (IOException e)
            {
                throw new RuntimeException( e );
            }
            projects.add(project);
            return project;
        }

        final IModel<List<IProject>> projectsModel = () -> projects;
        final IModel<IProject> selectedProjectModel = new Model<>();
        if ( ! projects.isEmpty() )
        {
            return projects.get(0);
        }

        final SelectProjectDialog dialog = new SelectProjectDialog( null, projectsModel, selectedProjectModel );
        dialog.setVisible( true );

        if ( selectedProjectModel.getObject() == null )
        {
            System.out.println("No project selected, terminating.");
            System.exit(1);
        } 
        return selectedProjectModel.getObject();
    }

    public static  void showError(String message)
    {
        showError("An error occurred",message,null);
    }

    public static  void showError(String message,Throwable t)
    {
        showError("An error occurred",message,t);
    }

    public static void showError(String title,String message) {
        showError(title,message,null);
    }

    public static void showError(String title,String message,Throwable t)
    {
        LOG.error( message,t);

        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("ERROR: "+message);
        if ( t != null ) {
            errorMsg.append("\n\n");
            final StringWriter writer = new StringWriter();
            try ( var pw = new PrintWriter(writer) )
            {
                t.printStackTrace( pw );
            }
            errorMsg.append( writer.getBuffer().toString() );
        }
        JOptionPane.showMessageDialog( null,errorMsg,title , JOptionPane.ERROR_MESSAGE);
    }
}