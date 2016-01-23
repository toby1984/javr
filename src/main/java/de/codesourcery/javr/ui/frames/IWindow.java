package de.codesourcery.javr.ui.frames;

import java.awt.Dimension;
import java.awt.Point;

public interface IWindow 
{
    public String getWindowId();
    
    public Point getLocation();
    
    public void setLocation(Point p);
    
    public Dimension getSize();
    
    public void setSize(Dimension size);
}
