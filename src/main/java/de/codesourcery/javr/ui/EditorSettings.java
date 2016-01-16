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
