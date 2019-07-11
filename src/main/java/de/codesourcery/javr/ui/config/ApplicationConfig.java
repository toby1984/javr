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
package de.codesourcery.javr.ui.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import de.codesourcery.javr.ui.EditorSettings;
import de.codesourcery.javr.ui.GlobalSettings;
import de.codesourcery.javr.ui.frames.IWindow;

public class ApplicationConfig implements IApplicationConfig 
{
    private EditorSettings editorSettings = new EditorSettings();
    private GlobalSettings globalSettings = new GlobalSettings();
    private List<WindowProperties> windowProperties = new ArrayList<>();

    public ApplicationConfig() {
    }
    
    public ApplicationConfig(ApplicationConfig other) 
    {
        this.editorSettings = other.editorSettings.createCopy();
        this.windowProperties = other.windowProperties.stream().map( WindowProperties::createCopy ).collect( Collectors.toCollection( ArrayList::new ) );
        this.globalSettings = other.globalSettings.createCopy();
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
    public GlobalSettings getGlobalSettings()
    {
        return globalSettings.createCopy();
    }

    @Override
    public void setGlobalSettings(GlobalSettings settings)
    {
        this.globalSettings = settings.createCopy();
    }

    @Override
    public IApplicationConfig createCopy() {
        return new ApplicationConfig(this);
    }

    @Override
    public void apply(IWindow window) 
    {
        final Optional<WindowProperties> props = windowProperties.stream().filter( w -> w.windowId.equals( window.getWindowId() ) ).findFirst();
        if ( props.isPresent() ) {
            System.out.println("Applying existing properties for "+window.getWindowId());
            props.get().apply( window );
        } else {
            System.out.println("Found NO properties for "+window.getWindowId());
        }
    }

    @Override
    public void save(IWindow window) 
    {
        final Optional<WindowProperties> props = windowProperties.stream().filter( w -> w.windowId.equals( window.getWindowId() ) ).findFirst();
        if ( props.isPresent() ) {
            System.out.println("Updating existing properties for "+window.getWindowId());
            props.get().populateFrom( window );
        }
        else
        {
            System.out.println("Storing new properties for "+window.getWindowId());
            final WindowProperties p = new WindowProperties();
            p.populateFrom( window );
            this.windowProperties.add( p );
        }
    }
}
