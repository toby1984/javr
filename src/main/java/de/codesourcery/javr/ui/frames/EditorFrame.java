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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.swing.JInternalFrame;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import de.codesourcery.javr.ui.panels.EditorPanel;

public class EditorFrame extends JInternalFrame 
{
	private final EditorPanel panel;
	
	public EditorFrame(IProject project,CompilationUnit compUnit,IApplicationConfigProvider appConfigProvider) throws IOException {

		panel = new EditorPanel(project, compUnit,appConfigProvider);
		
		getContentPane().add( panel );
	}
	
	public void save(File file) throws FileNotFoundException {
		panel.save(file);
	}
	
	public void setProject(IProject project,CompilationUnit unit) throws IOException {
		panel.setProject(project,unit);
	}
	
	public void compile() {
		panel.compile();
	}
}