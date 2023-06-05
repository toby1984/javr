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
package de.codesourcery.javr.ui.panels;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

public class AsyncStreamReaderPanel extends JPanel 
{
    private static final Logger LOG = Logger.getLogger(AsyncStreamReaderPanel.class);

    private static final AtomicInteger ID = new AtomicInteger();
    
    private final JTextArea textArea = new JTextArea();

    private final Object THREAD_LOCK = new Object();
    
    private volatile boolean printTimestamps=true;
    
    // @GuardedBy( THREAD_LOCK )    
    private boolean threadEnabled = false;
    // @GuardedBy( THREAD_LOCK )
    private Reader streamReader = null;
    // @GuardedBy( THREAD_LOCK )
    private InputStream stream = null;
    
    private volatile boolean terminate = false;
    
    private volatile boolean stopOutput = false;

    private volatile int maxBufferSize = 1024;
    
    private final Thread ioThread = new Thread("io-thread-"+ID.incrementAndGet()) { 

        private void closeReader() 
        {
            IOUtils.closeQuietly( streamReader );
            streamReader = null;
            stream = null;
            threadEnabled = false;
        }
        
        public void run() 
        {
            final char[] buffer = new char[20];           
            LOG.debug("Thread started.");
            while( ! terminate  ) 
            {
                synchronized(THREAD_LOCK) 
                {
                    if ( ! threadEnabled || streamReader == null ) 
                    {
                        try 
                        {
                            if ( LOG.isTraceEnabled() ) {
                                LOG.trace("Thread going to sleep (disabled, got IO stream: "+(streamReader != null)+")");
                            }
                            THREAD_LOCK.wait( 200 );
                        } catch (InterruptedException e) { /* loop */ }
                        continue;
                    } 
                    try 
                    {
                        if ( ! streamReader.ready() && stream.available() <= 0 ) 
                        {
                            continue;
                        }
                        
                        if ( LOG.isTraceEnabled() ) {
                            LOG.trace("Reading from buffer...");
                        } 
                        stopOutput = false;
                        while ( streamReader.ready() && ! stopOutput ) 
                        {
                            if ( LOG.isTraceEnabled() ) {
                                LOG.trace("Reading from buffer...");
                            }                            
                            final int len = streamReader.read( buffer );
                            if ( LOG.isTraceEnabled() ) {
                                LOG.trace("Got "+len+" characters");
                            }  
                            if ( len > 0 ) 
                            {
                                appendText( new String(buffer,0,len) );
                            } 
                            else 
                            {
                                appendText("\nEnd of input reached.");
                                LOG.debug("EOF reached");
                                closeReader();
                                break;
                            }
                        }
                    } 
                    catch(IOException e) 
                    {
                        if ( ! "stream closed".equalsIgnoreCase( e.getMessage() ) ) {
                            appendText("\nCAUGHT ERROR: "+e.getMessage());
                            if ( LOG.isDebugEnabled() ) {
                                LOG.info("run(): Caught "+e.getMessage(),e);
                            } else {
                                LOG.info("run(): Caught "+e.getMessage() );
                            }
                        } else {
                            LOG.debug("stream closed");
                        }
                        closeReader();
                    } 
                }
            }
            closeReader();
            LOG.debug("Thread terminated.");
        }
    };
    
    public AsyncStreamReaderPanel() 
    {
        textArea.setEditable( false );
        textArea.setColumns( 25 );
        setLayout( new GridBagLayout() );
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx = 1; cnstrs.weighty = 1;
        cnstrs.fill = GridBagConstraints.BOTH;
        add( new JScrollPane( textArea ) , cnstrs );

        addAncestorListener( new AncestorListener() {
            
            @Override
            public void ancestorRemoved(AncestorEvent event) 
            {
                synchronized (THREAD_LOCK) 
                {
                    threadEnabled = false;
                }
            }
            
            @Override
            public void ancestorMoved(AncestorEvent event) { }
            
            @Override
            public void ancestorAdded(AncestorEvent event) {
                synchronized (THREAD_LOCK) 
                {
                    threadEnabled = true;
                    THREAD_LOCK.notifyAll();
                }
            }
        });

        ioThread.setDaemon( true );
        ioThread.start();
    }    

    public void setInputStream(final InputStream stream) 
    {
        SwingUtilities.invokeLater( () -> 
        {
            stopOutput = true;
            synchronized (THREAD_LOCK) 
            {
                if ( streamReader != null ) {
                    IOUtils.closeQuietly( streamReader );
                }
                threadEnabled = stream != null ;
                this.stream = stream;
                streamReader = stream != null ? new InputStreamReader(stream) : null;
            }
        });
    }
    
    public void appendText(String msg) 
    {
        if ( printTimestamps && msg.contains("\n" ) ) 
        {
            final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            final String date = df.format( ZonedDateTime.now() );
            msg = msg.replace("\n" , "\n"+date+": ");
        }
        
        final String finalMsg = msg;
        final Runnable runnable = () -> 
        {
            String newString = textArea.getText() + finalMsg;
            final int maxSize = maxBufferSize;
            if ( newString.length() > maxSize ) 
            {
                newString = newString.substring( newString.length() - maxSize , newString.length() );
            }
            textArea.setText( newString );  
        };
        if ( SwingUtilities.isEventDispatchThread() ) 
        {
            runnable.run();
        } else {
            SwingUtilities.invokeLater( runnable);
        }
    }

    public void setMaxBufferSize(int maxBufferSize) 
    {
        if ( maxBufferSize < 1 ) {
            throw new IllegalArgumentException("Buffer too small");
        }
        this.maxBufferSize = maxBufferSize;
    }
    
    public void terminate() 
    {
        terminate = true;
    }

    public void setPrintTimestamps(boolean printTimestamps) {
        this.printTimestamps = printTimestamps;
    }
    
    public boolean isPrintTimestamps() {
        return printTimestamps;
    }
}