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

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.CompilerSettings;
import de.codesourcery.javr.assembler.arch.Architecture;
import de.codesourcery.javr.ui.config.ProjectConfiguration;
import de.codesourcery.javr.ui.config.ProjectConfiguration.OutputFormat;

public abstract class ProjectConfigWindow extends JPanel 
{
    private ProjectConfiguration toEdit;
    
    private JTextField projectName = new JTextField();
    private JTextField outputName = new JTextField();
    private JTextField compilationRoot = new JTextField();
    private JComboBox<OutputFormat> outputFormat = new JComboBox<>( OutputFormat.values() );
    private JComboBox<Architecture> architecture = new JComboBox<>( Architecture.values() );
    private JCheckBox failOnAddressOutOfBounds = new JCheckBox();
    private JCheckBox warnIfInOutCanBeUsed = new JCheckBox();
    private JTextField uploadCommand = new JTextField();
    
    public ProjectConfigWindow(ProjectConfiguration currentConfig) 
    {
        Validate.notNull(currentConfig, "currentConfig must not be NULL");
        this.toEdit = currentConfig.createCopy();
        
        projectName.setText( currentConfig.getProjectName() );
        outputName.setText( currentConfig.getOutputName() );
        compilationRoot.setText( currentConfig.getCompilationRoot() );
        outputFormat.setSelectedItem( currentConfig.getOutputFormat() );
        architecture.setSelectedItem( currentConfig.getArchitecture().getType() );
        failOnAddressOutOfBounds.setSelected( currentConfig.getCompilerSettings().isFailOnAddressOutOfRange() );
        uploadCommand.setText( currentConfig.getUploadCommand() );
        warnIfInOutCanBeUsed.setSelected( currentConfig.getCompilerSettings().isWarnIfInOutCanBeUsed() );
        
        final JButton save = new JButton("Save changes");
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> onCancel() );
        save.addActionListener( ev -> 
        {
        	toEdit.setCompilationRoot( compilationRoot.getText() );
            toEdit.setProjectName( projectName.getText() );
            toEdit.setOutputName( outputName.getText() );
            toEdit.setOutputFormat( (OutputFormat) outputFormat.getSelectedItem() );
            toEdit.setArchitecture( (Architecture) architecture.getSelectedItem() );
            toEdit.setUploadCommand( uploadCommand.getText() );

            final CompilerSettings settings = new CompilerSettings();
            settings.setFailOnAddressOutOfRange( failOnAddressOutOfBounds.isSelected() );
            settings.setWarnIfInOutCanBeUsed( warnIfInOutCanBeUsed.isSelected() );
            toEdit.setCompilerSettings( settings );
            
            onSave( toEdit );
        });
        
        setLayout( new GridBagLayout() );
        int y = 0;
        addRow( y++ , "Project name" , projectName );
        addRow( y++ , "Output name" , outputName );
        addRow( y++ , "Compilation root" , compilationRoot );
        addRow( y++ , "Output format" , outputFormat );
        addRow( y++ , "Architecture" , architecture );
        addRow( y++ , "Upload command" , uploadCommand);
        addRow( y++ , "Fail on out-of-bounds addresses" , failOnAddressOutOfBounds);
        addRow( y++ , "Warn if IN/OUT could be used" , warnIfInOutCanBeUsed );
        
        final JPanel buttonRow = new JPanel();
        buttonRow.setLayout( new FlowLayout() );
        buttonRow.add( save );
        buttonRow.add( cancel );
        
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.gridheight=1; cnstrs.gridwidth = 2;
        cnstrs.weightx = 0.8 ; cnstrs.weighty = 0d;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.insets = new Insets( 20 ,  0 ,  0 ,  0 );
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        add( buttonRow , cnstrs );
    }
    
    private void addRow( int y , String label , JComponent component) 
    {
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets( 10 ,  10 ,  0 ,  10 );
        cnstrs.gridheight=1; cnstrs.gridwidth = 1;
        cnstrs.weightx = 0.3d ; cnstrs.weighty = 0d;
        cnstrs.gridx = 0 ; cnstrs.gridy = y;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        add( new JLabel(label) , cnstrs );
        
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets( 10 ,  10 ,  0 ,  10 );
        cnstrs.gridheight=1; cnstrs.gridwidth = 1;
        cnstrs.weightx = 0.7d ; cnstrs.weighty = 0d;
        cnstrs.gridx = 1 ; cnstrs.gridy = y;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        add( component , cnstrs );
    }
    
    protected abstract void onSave(ProjectConfiguration config);
    
    protected abstract void onCancel();
    
}
