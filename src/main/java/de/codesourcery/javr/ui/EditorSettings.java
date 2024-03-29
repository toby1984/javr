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

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

public class EditorSettings 
{
    @SuppressWarnings("unused")
    private static final Logger LOG = Logger.getLogger(EditorSettings.class);
    
    private String indentString;
    
    private static final Map<SourceElement,Color> DEFAULT_COLORS = new HashMap<>();
    
    static
    {
        DEFAULT_COLORS.put( SourceElement.LABEL , new Color( 0x039b38 ) );
        DEFAULT_COLORS.put( SourceElement.NUMBER, new Color( 0xffb200) );
        DEFAULT_COLORS.put( SourceElement.REGISTER, Color.BLUE);
        DEFAULT_COLORS.put( SourceElement.MNEMONIC, Color.MAGENTA );
        DEFAULT_COLORS.put( SourceElement.COMMENT , Color.ORANGE );
        DEFAULT_COLORS.put( SourceElement.PREPROCESSOR, Color.PINK);
        DEFAULT_COLORS.put( SourceElement.TODO, Color.RED);     
    }

    private boolean displayLineNumbers=true;
    
    public enum SourceElement
    {
        LABEL,NUMBER,REGISTER,MNEMONIC,COMMENT,PREPROCESSOR,TODO
    }
    
    private final Map<SourceElement,Color> colors = new HashMap<>();
    
    public EditorSettings() 
    {
        colors.putAll( DEFAULT_COLORS );
    }
    
    public EditorSettings(EditorSettings editorSettings) 
    {
        this.indentString = editorSettings.indentString;
        this.colors.putAll( editorSettings.colors );
    }

    public boolean isDisplayLineNumbers()
    {
        return displayLineNumbers;
    }

    public void setDisplayLineNumbers(boolean displayLineNumbers)
    {
        this.displayLineNumbers = displayLineNumbers;
    }

    public String getIndentString()
    {
        return indentString;
    }
    
    public void setIndentString(String indentString) {
        this.indentString = indentString;
    }

    public void setColor(SourceElement elem, Color color) {
        this.colors.put( elem, color );
    }
    
    public Map<SourceElement, Color> getColors() {
        return new HashMap<>(colors);
    }
    
    public Color getColor(SourceElement elem) 
    {
        Validate.notNull(elem, "elem must not be NULL");
        final Color result = colors.get( elem );
        if ( result == null ) {
            return DEFAULT_COLORS.get(elem);
        }
        return result;
    }
    
    public EditorSettings createCopy() 
    {
        return new EditorSettings(this);
    }
}
