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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

public class ProcessWindow extends JDialog 
{
    private static final Logger LOG = Logger.getLogger(ProcessWindow.class);
    
    private final AsyncStreamReaderPanel stdOutPanel = new AsyncStreamReaderPanel();
    private final AsyncStreamReaderPanel stdErrPanel = new AsyncStreamReaderPanel();
    
    private volatile Process process;
    private volatile boolean terminatedByUser;
    
    private final JButton cancel = new JButton("Cancel");
    
    public ProcessWindow(String title,String msg,boolean modal) 
    {
        super((JFrame) null,title,modal);
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        addWindowListener( new WindowAdapter() 
        {
            @Override
            public void windowClosed(WindowEvent e) 
            {
                terminatedByUser = true;
                stop();
            }
        });
        
        getContentPane().setLayout( new GridBagLayout() );
        
        int y = 0;
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.weightx=1; cnstrs.weighty = 0.3;
        cnstrs.gridx = 0 ; cnstrs.gridy = y++;
        cnstrs.insets = new Insets( 10 , 0 , 10 , 0 );
        cnstrs.fill = GridBagConstraints.BOTH;
        final JTextArea msgArea = new JTextArea( msg );
        msgArea.setEditable( false );
        msgArea.setRows( 5 );
        msgArea.setWrapStyleWord( true );
        msgArea.setLineWrap(true);
        getContentPane().add( new JScrollPane(msgArea) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=1; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y++;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        getContentPane().add( new JLabel("Standard output:") , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=1; cnstrs.weighty = 0.35;
        cnstrs.gridx = 0 ; cnstrs.gridy = y++;
        cnstrs.fill = GridBagConstraints.BOTH;        
        getContentPane().add( stdOutPanel , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=1; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y++;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        getContentPane().add( new JLabel("Standard error:") , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=1; cnstrs.weighty = 0.35;
        cnstrs.gridx = 0 ; cnstrs.gridy = y++;
        cnstrs.fill = GridBagConstraints.BOTH;        
        getContentPane().add( stdErrPanel , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.weightx=1; cnstrs.weighty = 0;
        cnstrs.gridx = 0 ; cnstrs.gridy = y++;
        cnstrs.fill = GridBagConstraints.NONE;  
        cnstrs.insets = new Insets( 10 , 0 , 10 , 0 );
        cancel.addActionListener( ev -> 
        {
            if ( terminatedByUser ) {
                dispose();
            } else {
                terminatedByUser = true;
                cancel.setText("Close");
                stop();
            }
        });
        getContentPane().add( cancel , cnstrs );
        
        setPreferredSize( new Dimension(400,400 ) );
    }
    
    private void stop() 
    {
        if ( process != null ) 
        {
            process.destroyForcibly();
            process = null;
        }
        stdOutPanel.terminate();
        stdErrPanel.terminate();        
    }
    
    public void execute(final List<String> command) 
    {
        final Thread t = new Thread() 
        {
            public void run() 
            {
                try 
                {
                    SwingUtilities.invokeLater( () -> 
                    {
                        pack();
                        setLocationRelativeTo( null );
                        setVisible( true );
                    });
                    
                    LOG.debug("Starting process "+command.stream().collect(Collectors.joining(" ") ) );
                    final ProcessBuilder builder = new ProcessBuilder( command );                    
                    final Process process = builder.start();
                    final InputStream stdOut = process.getInputStream();
                    final InputStream stdErr = process.getErrorStream();
                    stdOutPanel.setInputStream( stdOut );
                    stdErrPanel.setInputStream( stdErr );
                    final int exitCode = process.waitFor();
                    LOG.debug("Process "+command+" exited with "+exitCode+" (terminated by user: "+terminatedByUser+")");
                    if ( terminatedByUser ) {
                        stdOutPanel.appendText("\nProcess terminated by user.");
                    } else if ( exitCode != 0 ) {
                        stdErrPanel.appendText("\nProcess failed with exit code "+exitCode);
                    } else {
                        stdOutPanel.appendText("\nProcess finished successfully.");
                        terminatedByUser = true;
                        SwingUtilities.invokeLater( () -> cancel.setText("Close") );
                    }
                    Thread.sleep(500);
                    Thread.yield();
                    process.destroy();
                } 
                catch (Exception e) 
                {
                    throw new RuntimeException(e);
                }                   
            }
        };
        t.setDaemon( true );
        t.start();
    }
}