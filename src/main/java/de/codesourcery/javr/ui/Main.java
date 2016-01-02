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

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

import de.codesourcery.javr.assembler.IArchitecture;
import de.codesourcery.javr.assembler.Lexer;
import de.codesourcery.javr.assembler.Parser;
import de.codesourcery.javr.assembler.Scanner;
import de.codesourcery.javr.assembler.arch.ATMega88;

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
