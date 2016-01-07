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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Stream;

import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.arch.IArchitecture;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.util.FileResource;
import de.codesourcery.javr.assembler.util.Resource;
import de.codesourcery.javr.assembler.util.StringResource;

public class Main 
{
    private IConfig config; 
    private IConfigProvider configProvider;
    private EditorFrame editorFrame;
    private File lastDisassembledFile = new File("/home/tobi/atmel/bootloader/BootLoader88.raw_stripped");
    private File lastSourceFile = new File("/home/tobi/atmel/asm/BootLoader88_original2.asm");
    
    public static void main(String[] args) 
    {
        SwingUtilities.invokeLater( () -> new Main().run() );
    }
    
    private void init() 
    {
        config = new IConfig() {
            
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
    
    private void run() 
    {
        init();
        
        final JDesktopPane pane = new JDesktopPane();
        addWindows( pane );
        
        final JFrame frame = new JFrame();
        frame.setJMenuBar( createMenu(frame) );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        
        frame.setPreferredSize( new Dimension(640,480));
        frame.setMinimumSize( new Dimension(640,480));
        frame.setContentPane( pane );
        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible( true );
        frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        
        Stream.of( pane.getAllFrames() ).forEach( internalFrame -> 
        {
            try {
                internalFrame.setMaximum(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    private JMenuBar createMenu(JFrame parent) 
    {
        final JMenuBar result = new JMenuBar();
        
        final JMenu menu = new JMenu("File");
        result.add( menu );
        addItem( menu , "Disassemble" , () -> doWithFile( parent , true , lastDisassembledFile, file -> 
        {
            disassemble(file);
            lastDisassembledFile = file;
        }));
        
        addItem( menu , "Save source" , () -> doWithFile( parent , false , lastSourceFile, file -> 
        {
            editorFrame.save(file); 
            lastSourceFile = file;
        }));  
        addItem( menu , "Load source" , () -> doWithFile( parent , true , lastSourceFile, file -> 
        {
            final CompilationUnit unit = new CompilationUnit( new FileResource(file , Resource.ENCODING_UTF ) );
            editorFrame.setCompilationUnit( unit );
            lastSourceFile = file;
        })); 
        return result;
    }
    
    private void disassemble(File file) throws IOException 
    {
        final byte[] data = FileUtils.readFileToByteArray( file );
        System.out.println("Disassembling "+data.length+" bytes");
        String disassembly = config.getArchitecture().disassemble( data , data.length , false , 0 , true );
        disassembly = "; disassembled "+data.length+" bytes from "+file.getAbsolutePath()+"\n"+disassembly;
        
        final StringResource res = new StringResource(file.getAbsolutePath(), disassembly );
        final CompilationUnit unit = new CompilationUnit(res);
        editorFrame.setCompilationUnit( unit );        
    }
    
    protected static void fail(Exception e) 
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintWriter writer = new PrintWriter( out );
        e.printStackTrace( writer );
        
        final String body = "Error: "+e.getMessage()+"\n\n"+new String( out.toByteArray() );
        JOptionPane.showMessageDialog(null, body , "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    protected interface ThrowingConsumer<T> {
        public void apply(T obj) throws Exception;
    }
    
    protected interface ThrowingRunnable {
        public void run() throws Exception;
    }    
    
    private void doWithFile(JFrame parent,boolean openDialog,File file , ThrowingConsumer<File> handler) throws Exception 
    {
        final JFileChooser chooser = new JFileChooser();
        if ( file != null ) {
            chooser.setSelectedFile( file );
        }
        final int result = openDialog ? chooser.showOpenDialog( parent ) : chooser.showSaveDialog( parent );
        if ( result == JFileChooser.APPROVE_OPTION ) 
        {
            handler.apply( chooser.getSelectedFile() );
        }
    }
    
    private void addItem(JMenu menu,String label,ThrowingRunnable eventHandler) 
    {
        final JMenuItem item = new JMenuItem( label );
        item.addActionListener( ev -> 
        {
            try {
                eventHandler.run();
            } catch (Exception e) {
                fail(e);
            } 
        });
        menu.add( item );
    }

    private void addWindows(JDesktopPane pane) 
    {
        editorFrame = new EditorFrame( configProvider );
        editorFrame.setResizable(true);
        editorFrame.setMaximizable(true);
        editorFrame.pack();
        editorFrame.setVisible( true );
        pane.add( editorFrame );
    }
}