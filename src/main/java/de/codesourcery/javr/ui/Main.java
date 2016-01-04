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
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

import de.codesourcery.javr.assembler.Binary;
import de.codesourcery.javr.assembler.ResourceFactory;
import de.codesourcery.javr.assembler.Segment;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.Resource;

public class Main 
{
    private final IConfigProvider configProvider;
    
    public static void main(String[] args) 
    {
        SwingUtilities.invokeLater( () -> new Main().run() );
    }
    
    public Main() 
    {
        final IConfig config = new IConfig() {
            
            private final IArchitecture arch = new ATMega88();
            
            @Override
            public Parser createParser() 
            {
                final Parser p = new Parser();
                p.setArchitecture( arch );
                return p;
            }
            
            @Override
            public IArchitecture getArchitecture() {
                return arch;
            }

            @Override
            public Lexer createLexer(Scanner s) {
                return new Lexer(s);
            }

            @Override
            public String getEditorIndentString() {
                return "  ";
            }

            @Override
            public ResourceFactory getResourceFactory() 
            {
                String path = Paths.get(".").toAbsolutePath().normalize().toString();
                while ( path.length() > File.pathSeparator.length() && path.endsWith( File.pathSeparator ) ) {
                    path = path.substring(0 , path.length()-1 );
                }
                final String pwd = path;
                final File pwdFile = new File( path );
                return new ResourceFactory() 
                {
                    @Override
                    public Resource resolveResource(Resource parent, String child) throws IOException {
                        return new FileResource( new File(pwdFile,child) ,  Resource.ENCODING_UTF );
                    }
                    
                    @Override
                    public Resource getResource(Binary binary, Segment segment) throws IOException 
                    {
                        final String suffix;
                        switch( segment ) 
                        {
                            case EEPROM: suffix = ".eeprom"; break;
                            case FLASH:  suffix = ".flash"; break;
                            case SRAM:   suffix = ".sram"; break;
                            default: throw new RuntimeException("Unhandled segment type: "+segment);
                        }
                        return new FileResource( new File( pwd + File.separatorChar + binary.getIdentifier()+ suffix )  , Resource.ENCODING_UTF );
                    }
                };
            }
        };
        configProvider = new IConfigProvider() {
            
            @Override
            public IConfig getConfig() {
                return config;
            }
        };
    }
    
    public void run() 
    {
        final JDesktopPane pane = new JDesktopPane();
        setup( pane );
        
        final JFrame frame = new JFrame();
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        frame.setPreferredSize( new Dimension(640,480));
        frame.setMinimumSize( new Dimension(640,480));
        frame.setContentPane( pane );
        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible( true );
    }

    private void setup(JDesktopPane pane) 
    {
        final JInternalFrame editorFrame = new EditorFrame( configProvider );
        editorFrame.setResizable(true);
        editorFrame.pack();
        editorFrame.setVisible( true );
        pane.add( editorFrame );
    }
}
