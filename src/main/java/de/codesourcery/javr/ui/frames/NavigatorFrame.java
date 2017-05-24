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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JInternalFrame;
import javax.swing.JPopupMenu;

import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.IProjectProvider;
import de.codesourcery.javr.ui.IProjectProvider.IProjectListener;
import de.codesourcery.javr.ui.panels.FileSystemBrowser;
import de.codesourcery.javr.ui.panels.FileSystemBrowser.DirNode;

public class NavigatorFrame extends JInternalFrame implements IWindow
{
    private final FileSystemBrowser browser;
    
    public NavigatorFrame(IProjectProvider provider) 
    {
        super( "Filesystem" );
        
        this.browser = new FileSystemBrowser( provider.getProject().getConfiguration().getBaseDir() );
        
        provider.addProjectListener( new IProjectListener() {
            
            @Override
            public void projectOpened(IProject project) {
                browser.setFolder( project.getConfiguration().getBaseDir() );
            }
            
            @Override
            public void projectClosed(IProject project) {
            }
        });
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx = 1; cnstrs.weighty = 1;
        cnstrs.gridx=0; cnstrs.gridy = 0;
        cnstrs.gridheight = 1; cnstrs.gridwidth = 1;        
        
        getContentPane().setLayout( new GridBagLayout() );
        getContentPane().add( browser , cnstrs );        
    }
            
    public void setRootDirectory(File file) {
        setTitle( file.getAbsolutePath() );
        browser.setFolder( file );
    }

    public void setMenuSupplier(Function<DirNode,JPopupMenu> supplier) 
    {
        browser.setMenuSupplier( supplier );
    }         

    public void fileRemoved(File file) {
        browser.fileRemoved( file );
    }
    
    public void refreshPath() 
    {
        browser.pathChanged( browser.getRoot() );
    }    
    
    public void fileAdded(File file) {
        browser.pathChanged( file );
    }
    
    public void refreshPath(File topLevelDir) {
        browser.pathChanged( topLevelDir );
    }
    
    public void setSelectionHandler(Consumer<File> handler) 
    {
        browser.setSelectionHandler( handler );
    }

    @Override
    public String getWindowId() {
        return "navigator";
    }
}