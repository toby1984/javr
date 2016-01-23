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

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;

public class EditorSettings 
{
    private String indentString;
    
    public static enum SourceElement 
    {
        LABEL,NUMBER,REGISTER,MNEMONIC,COMMENT,PREPROCESSOR
    }
    
    private Map<SourceElement,Color> colors = new HashMap<>();
    
    public EditorSettings() 
    {
        colors.put( SourceElement.LABEL , Color.GREEN );
        colors.put( SourceElement.NUMBER, Color.BLUE);
        colors.put( SourceElement.REGISTER, Color.BLUE);
        colors.put( SourceElement.MNEMONIC, Color.BLACK );
        colors.put( SourceElement.COMMENT , Color.ORANGE );
        colors.put( SourceElement.PREPROCESSOR, Color.PINK);
    }
    
    public EditorSettings(EditorSettings editorSettings) 
    {
        this.indentString = editorSettings.indentString;
        this.colors.putAll( editorSettings.colors );
    }

    public String getIndentString() 
    {
        return indentString;
    }
    
    public void setIndentString(String indentString) {
        this.indentString = indentString;
    }
    
    public Map<SourceElement, Color> getColors() {
        return new HashMap<>(colors);
    }
    
    public Color getColor(SourceElement elem,Color defaultColor) 
    {
        final Color result = colors.get( elem );
        if ( result == null ) {
            colors.put( elem , defaultColor );
            return defaultColor;
        }
        return result;
    }
    
    public void setColors(Map<SourceElement, Color> colors) 
    {
        Validate.notNull(colors, "colors must not be NULL");
        this.colors.clear();
        this.colors.putAll(colors);
    }

    public EditorSettings createCopy() 
    {
        return new EditorSettings(this);
    }
}
