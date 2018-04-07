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

import java.io.IOException;
import java.util.Optional;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.symbols.SymbolTable;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.IConfigProvider;
import de.codesourcery.javr.ui.config.ProjectConfiguration;

public interface IProject extends ResourceFactory,IConfigProvider
{
    public static final String PROJECT_FILE = ".javr_project.properties";
    
    public interface IProjectChangeListener 
    {
    	public default void compilationFinished(IProject project,boolean sucess) {}
    	
    	public default void unitAdded(Project project,CompilationUnit newUnit) {}
    	
    	public default void unitRemoved(Project project,CompilationUnit newUnit) {}   	
    }
    
    public IArchitecture getArchitecture();
    
    public IObjectCodeWriter getObjectCodeWriter();
    
    public CompilationUnit getCompileRoot();
    
    public ProjectConfiguration getConfiguration();
    
    public void setConfiguration(ProjectConfiguration other) throws IOException;
    
    public boolean canUploadToController();
    
    public void uploadToController() throws IOException; 
    
    public boolean compile() throws IOException;
    
    public Optional<CompilationUnit> maybeGetCompilationUnit(Resource resource); 
    
    public CompilationUnit getCompilationUnit(Resource resource);
    
    public SymbolTable getGlobalSymbolTable();

	public void removeCompilationUnit(CompilationUnit newUnit);
	
	public void addProjectChangeListener(IProjectChangeListener listener);
	
	public void removeProjectChangeListener(IProjectChangeListener listener);
}
