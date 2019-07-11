package de.codesourcery.javr.ui;

import de.codesourcery.javr.ui.config.IApplicationConfig;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

public abstract class GlobalConfigurationWindow extends JDialog
{
    private IApplicationConfigProvider provider;

    public final JCheckBox resetDialogs = new JCheckBox();

    public GlobalConfigurationWindow(IApplicationConfigProvider provider)
    {
        super( (JFrame) null, "Application Settings", true);
        this.provider = provider;

        // save button
        final JButton saveButton = new JButton("Save");

        saveButton.addActionListener( ev ->
        {
            if ( resetDialogs.isSelected() )
            {
                final IApplicationConfig config = provider.getApplicationConfig();
                config.updateGlobalSettings( x-> x.setDefaultProjectToOpen( null ) );
                saveConfiguration( config );
            }
            dispose();
        });

        // cancel button
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener( ev -> this.dispose() );

        // options panel
        final JPanel options = new JPanel();
        options.setLayout(  new GridLayout(1,2 ) );
        options.add( new JLabel( "Reset dialogs to defaults?" ) );
        options.add( resetDialogs );

        // setup UI layout
        getContentPane().setLayout( new GridBagLayout() );
        GridBagConstraints cnstrs = new GridBagConstraints();

        // add options panel
        cnstrs.gridx = 0 ; cnstrs.gridy = 0;
        cnstrs.gridwidth = 2 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 1; cnstrs.weighty = 0.9;
        cnstrs.fill = GridBagConstraints.BOTH;
        getContentPane().add( options, cnstrs );

        // add cancel button
        cnstrs.gridx = 0 ; cnstrs.gridy = 1;
        cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 0.5; cnstrs.weighty = 0.1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        getContentPane().add( cancel, cnstrs );

        // add apply button
        cnstrs.gridx = 1 ; cnstrs.gridy = 1;
        cnstrs.gridwidth = 1 ; cnstrs.gridheight = 1;
        cnstrs.weightx = 0.5; cnstrs.weighty = 0.1;
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        getContentPane().add( saveButton, cnstrs );

        pack();
        setLocationRelativeTo( null );
        setVisible( true );
    }

    protected abstract void saveConfiguration(IApplicationConfig config);
}
