package de.codesourcery.javr.ui.config;

import java.util.function.Consumer;

public interface IApplicationConfigProvider 
{
    public IApplicationConfig getApplicationConfig();
    
    public void setApplicationConfig(IApplicationConfig config);
    
    public void addChangeListener(Consumer<IApplicationConfig> listener);
    
    public void removeChangeListener(Consumer<IApplicationConfig> listener);
}
