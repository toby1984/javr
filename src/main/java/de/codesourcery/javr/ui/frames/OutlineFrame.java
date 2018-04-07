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
package de.codesourcery.javr.ui.frames;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.symbols.Symbol;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.IProject.IProjectChangeListener;
import de.codesourcery.javr.ui.config.IModel;
import de.codesourcery.javr.ui.panels.OutlinePanel;

public class OutlineFrame extends JInternalFrame implements IWindow , IProjectChangeListener
{
	private IModel<CompilationUnit> model = new IModel<CompilationUnit>() 
	{
		private CompilationUnit unit;
		
		@Override
		public CompilationUnit getObject() {
			return unit;
		}
		public void setObject(CompilationUnit obj) 
		{
			this.unit = obj;
			panel.setModel(this);
		}
	};
	
	private final OutlinePanel panel = new OutlinePanel(model);
	
	public OutlineFrame() 
	{
		super("Outline");
		
		getContentPane().setLayout( new GridBagLayout() );		
		
		// add checkboxes
		final JPanel checkboxes = new JPanel();
		
		checkboxes.setLayout( new FlowLayout() );
		final JCheckBox cb1 = new JCheckBox("Globals",panel.isShowGlobalLabels());
		cb1.addActionListener( ev -> 
		{
			panel.setShowGlobalLabels( cb1.isSelected() );
		});
		final JCheckBox cb11 = new JCheckBox("Locals",panel.isShowLocalLabels());
		cb11.addActionListener( ev -> 
		{
			panel.setShowLocalLabels( cb11.isSelected() );
		});		
		
		final JCheckBox cb2 = new JCheckBox("Equ",panel.isShowEqu());
		cb2.addActionListener( ev -> 
		{
			panel.setShowEqu( cb2.isSelected() );
		});
		final JCheckBox cb3 = new JCheckBox("Macros",panel.isShowMacros());
		cb3.addActionListener( ev -> 
		{
			panel.setShowMacros( cb3.isSelected() );
		});		
		Arrays.asList(cb1,cb11,cb2,cb3).forEach( checkboxes::add );
		
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1;
		cnstrs.weighty = 0;
		cnstrs.gridwidth = cnstrs.gridheight = 1;
		cnstrs.gridx=cnstrs.gridy=0;
		getContentPane().add( checkboxes , cnstrs );
		
		// add panel with TextField to restrict by symbol name
		final JPanel textfieldPanel = new JPanel();
		textfieldPanel.setLayout( new GridBagLayout() );
		
		final JTextField pattern = new JTextField( panel.getPattern() );
		pattern.setColumns( 15 );
		pattern.addActionListener( ev -> panel.setPattern( pattern.getText() ) );
		pattern.getDocument().addDocumentListener( new DocumentListener() 
		{
			@Override public void insertUpdate(DocumentEvent e) { panel.setPattern( pattern.getText() ); }
			@Override public void removeUpdate(DocumentEvent e) { panel.setPattern( pattern.getText() ); }
			@Override public void changedUpdate(DocumentEvent e) { panel.setPattern( pattern.getText() ); }
		});
		
		final JButton button = new JButton("Reset");
		button.addActionListener( ev -> pattern.setText( null ) );
		
		cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.HORIZONTAL;
		cnstrs.weightx = 0.7;
		cnstrs.weighty = 0;
		cnstrs.gridwidth = cnstrs.gridheight = 1;
		cnstrs.gridx=0;
		cnstrs.gridy=0;	
		cnstrs.insets = new Insets( 0,10,10,10 );
		textfieldPanel.add(pattern,cnstrs);
		
		cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.HORIZONTAL;
		cnstrs.weightx = 0.3;
		cnstrs.weighty = 0;
		cnstrs.gridwidth = cnstrs.gridheight = 1;
		cnstrs.gridx=1;
		cnstrs.gridy=0;		
		cnstrs.insets = new Insets( 0,0,10,10 );
		textfieldPanel.add(button,cnstrs);
		
		cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.HORIZONTAL;
		cnstrs.weightx = 1;
		cnstrs.weighty = 0;
		cnstrs.gridwidth = cnstrs.gridheight = 1;
		cnstrs.gridx=0;
		cnstrs.gridy=1;		
		getContentPane().add( textfieldPanel , cnstrs );
		
		// add panel with JTable
		cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = cnstrs.weighty = 1;
		cnstrs.gridwidth = cnstrs.gridheight = 1;
		cnstrs.gridx=0;
		cnstrs.gridy=2;

		panel.setPreferredSize( new Dimension(400,200 ) );
		getContentPane().add( panel , cnstrs );
		
		setMinimumSize( new Dimension(50,50) );
		pack();
	}

	public void setCompilationUnit(CompilationUnit unit) 
	{
		model.setObject( unit );		
	}
	
	@Override
	public String getWindowId() 
	{
		return "outline";
	}
	
	@Override
	public void compilationFinished(IProject project, boolean sucess) 
	{
		model.setObject( project.getCompileRoot() );
	}
	
	public void setDoubleClickListener(Consumer<Symbol> doubleClickListener) {
		this.panel.setDoubleClickListener( doubleClickListener );
	}
}