package de.codesourcery.javr.ui.config;

import de.codesourcery.javr.ui.EditorSettings;

public interface IApplicationConfig 
{
    public EditorSettings getEditorSettings();
    
    public void setEditorSettings(EditorSettings settings);
    
    public IApplicationConfig createCopy();
}
