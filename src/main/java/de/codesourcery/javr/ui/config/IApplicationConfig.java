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
package de.codesourcery.javr.ui.config;

import java.awt.Dimension;
import java.awt.Point;

import org.apache.log4j.Logger;

import de.codesourcery.javr.ui.EditorSettings;
import de.codesourcery.javr.ui.frames.IWindow;

public interface IApplicationConfig 
{
    public static final class WindowProperties 
    {
        public String windowId;
        public int x;
        public int y;
        public int width;
        public int height;
        
        public WindowProperties() {
        }
        
        public WindowProperties(WindowProperties other) {
            this.windowId = other.windowId;
            this.x = other.x;
            this.y = other.y;
            this.width = other.width;
            this.height = other.height;
        }
        
        public WindowProperties createCopy() {
            return new WindowProperties(this);
        }
        
        public void apply(IWindow window) 
        {
            Logger.getLogger(IApplicationConfig.class).info("Applying "+this+" to "+window.getWindowId());
            window.setLocation( new Point(x,y) );
            window.setSize( new Dimension(width,height) );
        }
        
        public WindowProperties populateFrom(IWindow window) 
        {
            this.windowId = window.getWindowId();
            this.x = window.getLocation().x;
            this.y = window.getLocation().y;
            this.width = window.getSize().width;
            this.height = window.getSize().height;
            
            Logger.getLogger(IApplicationConfig.class).info("Stored properties "+this+" of "+window.getWindowId());
            return this;
        }
        
        @Override
        public String toString() {
            return "windowId: "+windowId+" , (x,y): "+x+","+y+" , (width,height): "+width+","+height;
        }
    }
    
    public EditorSettings getEditorSettings();
    
    public void setEditorSettings(EditorSettings settings);
    
    public IApplicationConfig createCopy();
    
    public void apply(IWindow window);
    
    public void save(IWindow window);
}
