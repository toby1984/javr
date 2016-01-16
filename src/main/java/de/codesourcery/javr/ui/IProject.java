package de.codesourcery.javr.ui;

import java.io.IOException;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.IObjectCodeWriter;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.arch.IArchitecture;

public interface IProject extends ResourceFactory,IConfigProvider
{
    public static final String PROJECT_FILE = ".javr_project.properties";
    
    public IArchitecture getArchitecture();
    
    public IObjectCodeWriter getObjectCodeWriter();
    
    public CompilationUnit getCompileRoot();
    
    public ProjectConfiguration getConfiguration();
    
    public void setConfiguration(ProjectConfiguration other);
    
    public boolean canUploadToController();
    
    public void uploadToController() throws IOException; 
    
    public boolean compile() throws IOException;
}
