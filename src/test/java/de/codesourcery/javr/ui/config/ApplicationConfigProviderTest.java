package de.codesourcery.javr.ui.config;

import java.awt.Dimension;
import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import de.codesourcery.javr.ui.frames.IWindow;
import junit.framework.TestCase;

public class ApplicationConfigProviderTest extends TestCase
{
    private static final class MyWindow implements IWindow {

        private String windowId;
        private Dimension size;
        private Point location;

        public MyWindow()
        {
        }

        public MyWindow(String windowId) {
            this.windowId = windowId;
        }

        @Override
        public boolean equals(Object o)
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            final MyWindow myWindow = (MyWindow) o;
            return Objects.equals( windowId, myWindow.windowId ) && Objects.equals( size, myWindow.size ) && Objects.equals( location, myWindow.location );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( windowId, size, location );
        }

        @Override
        public String getWindowId()
        {
            return windowId;
        }

        @Override
        public Point getLocation()
        {
            return location;
        }

        @Override
        public void setLocation(Point p)
        {
            this.location = new Point( p );
        }

        @Override
        public Dimension getSize()
        {
            return size;
        }

        @Override
        public void setSize(Dimension size)
        {
            this.size = new Dimension( size );
        }
    }

    public void testSaveWindowProperties() throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ApplicationConfig config = new ApplicationConfig();

        IWindow w = new MyWindow("myWindow");
        w.setLocation( new Point( 2, 3 ) );
        w.setSize( new Dimension( 4, 5 ) );
        config.save( w );
        ApplicationConfigProvider.save( config, bout );

        System.out.println( new String( bout.toByteArray(), StandardCharsets.UTF_8 ) );

        final ApplicationConfig loaded = (ApplicationConfig) ApplicationConfigProvider.load( new ByteArrayInputStream( bout.toByteArray() ) );

        IWindow w2 = new MyWindow("myWindow");
        loaded.apply( w2 );
        assertEquals( "Window properties differ", w, w2 );
    }
}