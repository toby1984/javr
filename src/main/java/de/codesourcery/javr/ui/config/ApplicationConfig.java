package de.codesourcery.javr.ui.config;

import de.codesourcery.javr.ui.EditorSettings;

public class ApplicationConfig implements IApplicationConfig 
{
    private EditorSettings editorSettings = new EditorSettings();
    
    public ApplicationConfig() {
    }
    
    public ApplicationConfig(ApplicationConfig other) 
    {
        this.editorSettings = other.editorSettings.createCopy();
    }
    
    @Override
    public EditorSettings getEditorSettings() {
        return editorSettings.createCopy();
    }

    @Override
    public void setEditorSettings(EditorSettings settings) {
        this.editorSettings = settings.createCopy();
    }

    @Override
    public IApplicationConfig createCopy() {
        return new ApplicationConfig(this);
    }
}
