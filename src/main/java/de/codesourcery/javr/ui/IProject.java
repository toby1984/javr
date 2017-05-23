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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.ui.config.ProjectConfiguration;

public interface IProject extends ResourceFactory
{
    public static final String PROJECT_FILE = ".javr_project.properties";
    
    public static final class CheckResult 
    {
        public boolean uploadPossible;
        public String message;
        
        private CheckResult(boolean yesNo,String msg) {
            Validate.notBlank( msg , "msg must not be NULL or blank");
            this.uploadPossible = yesNo;
            this.message = msg;
        }
        
        public static CheckResult uploadPossible(String message) { return new CheckResult(true,message); };
        public static CheckResult uploadNotPossible(String message) { return new CheckResult(false,message); };
    }
    
    public static enum ProjectType 
    {
        /**
         * Compiling this project produces one or more executables.
         * 
         * Compilation units that have a 'main' function are considered to
         * be compilation roots.
         * 
         * @see IProject#getCompilationRoots()
         * @see CompilationUnit
         */
        EXECUTABLE,
        /**
         * Compiling this project produces relocatable files, one for
         * each compilation unit.
         * 
         * @see IProject#getCompilationRoots()
         * @see CompilationUnit
         */
        LIBRARY;
    }
    
    public interface IProjectChangeListener 
    {
    	public default void compilationFinished(IProject project,boolean sucess) {}
    	
    	public default void unitAdded(Project project,CompilationUnit newUnit) {}
    	
    	public default void unitRemoved(Project project,CompilationUnit newUnit) {}   	
    }
    
    public Long getID();
    
    public void setID(long projectId);
    
    public IArchitecture getArchitecture();
    
    public ProjectConfiguration getConfiguration();
    
    public void setConfiguration(ProjectConfiguration config);
    
    public CheckResult checkCanUploadToController();
    
    public void uploadToController() throws IOException; 
    
    public boolean compile() throws IOException;
    
    public Optional<CompilationUnit> maybeGetCompilationUnit(Resource resource); 
    
    public CompilationUnit getCompilationUnit(Resource resource);
    
	public void removeCompilationUnit(CompilationUnit newUnit);
	
	public void addProjectChangeListener(IProjectChangeListener listener);
	
	public void removeProjectChangeListener(IProjectChangeListener listener);
	
    /**
     * Returns the root {@link CompilationUnit}s that will each be passed to the compiler.
     * 
     * @return
     */
    public List<CompilationUnit> getCompilationRoots() throws IOException;
    
    public default boolean hasProjectType(ProjectType type) 
    {
        return type.equals( getProjectType() );
    }
    
    public ProjectType getProjectType();
}