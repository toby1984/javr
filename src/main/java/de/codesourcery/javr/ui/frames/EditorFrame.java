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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.JInternalFrame;
import javax.swing.JTabbedPane;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import de.codesourcery.javr.ui.panels.EditorPanel;

public class EditorFrame extends JInternalFrame implements IWindow
{
    private final JTabbedPane tabbedPane; 
    private final IApplicationConfigProvider appConfigProvider;
    private final MessageFrame messageFrame;
    
	private final List<EditorPanel> editors = new ArrayList<>();
	
	public EditorFrame(IProject project,CompilationUnit compUnit,IApplicationConfigProvider appConfigProvider,MessageFrame messageFrame) throws IOException {

	    this.messageFrame = messageFrame;
	    this.appConfigProvider = appConfigProvider;
	    
		tabbedPane = new JTabbedPane();
		openEditor( project , compUnit );
		
		messageFrame.setDoubleClickListener( this::gotoMessage );
		
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx = 1; cnstrs.weighty = 1;
        cnstrs.gridx=0; cnstrs.gridy = 0;
        cnstrs.gridheight = 1; cnstrs.gridwidth = 1;		
        
        getContentPane().setLayout( new GridBagLayout() );
		getContentPane().add( tabbedPane , cnstrs );
	}
	
	public void gotoMessage(CompilationMessage msg) 
	{
        final Optional<EditorPanel> editor = editors.stream().filter( ed -> ed.getCompilationUnit().hasSameResourceAs( msg.unit ) ).findFirst();
        editor.ifPresent( panel -> 
        {
            tabbedPane.setSelectedComponent( panel );
            panel.gotoMessage( msg );
        });
	}
	
	public EditorPanel openEditor(IProject project,CompilationUnit unit) throws IOException 
	{
	    Optional<EditorPanel> existing = editors.stream().filter( editor -> editor.getProject().equals( project ) && 
	                                                                    editor.getCompilationUnit().hasSameResourceAs( unit ) ).findFirst();
	    
	    if ( ! existing.isPresent() ) {
	        existing = Optional.of( addEditor(project,unit ) );
	    }
	    
        tabbedPane.setSelectedComponent( existing.get() );
        return existing.get();
	}
	
	private EditorPanel addEditor(IProject project,CompilationUnit unit) throws IOException 
	{
	    final EditorPanel result = createEditor(project,unit);
	    this.editors.add( result );
	    tabbedPane.addTab( unit.getResource().getName() , result );
	    return result;
	}
 	
	private EditorPanel createEditor(IProject project,CompilationUnit compUnit) throws IOException
	{
	    return new EditorPanel(project, compUnit,appConfigProvider,messageFrame);
	}
	
	private EditorPanel currentEditor() 
	{
	    return (EditorPanel ) tabbedPane.getSelectedComponent();
	}
	
	public void save(File file) throws FileNotFoundException 
	{
		currentEditor().save(file);
	}
	
	public void setProject(IProject project,CompilationUnit unit) throws IOException {
		currentEditor().setProject(project,unit);
	}
	
	public void compile() 
	{
	    currentEditor().compile();
	}

    @Override
    public String getWindowId() {
        return "editorwindow";
    }
}