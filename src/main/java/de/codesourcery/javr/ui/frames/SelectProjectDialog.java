package de.codesourcery.javr.ui.frames;

import java.awt.Component;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import de.codesourcery.javr.ui.GridBagLayoutHelper;
import de.codesourcery.javr.ui.IProject;
import de.codesourcery.javr.ui.config.IModel;

public class SelectProjectDialog extends JDialog
{
    private static final class MyComboModel<T> extends AbstractListModel<T> implements ComboBoxModel<T> {

        private final IModel<List<T>> choicesModel;
        private final IModel<T> selectionModel;

        public MyComboModel(IModel<List<T>> choicesModel, IModel<T> selectionModel) {

            this.choicesModel = choicesModel;
            this.selectionModel = selectionModel;
        }

        @Override
        public void setSelectedItem(Object anItem)
        {
            selectionModel.setObject( (T) anItem );
        }

        @Override
        public Object getSelectedItem()
        {
            return selectionModel.getObject();
        }

        @Override
        public int getSize()
        {
            return choicesModel.getObject().size();
        }

        @Override
        public T getElementAt(int index)
        {
            return choicesModel.getObject().get( index );
        }
    }

    public SelectProjectDialog(Frame owner, IModel<List<IProject>> availableProjects, IModel<IProject> selectionModel)
    {
        super( owner, "Select project to open");
        setModal( true );

        setLocationRelativeTo( null );

        final ComboBoxModel<IProject> model = new MyComboModel<>( availableProjects, selectionModel );
        final JComboBox<IProject> combo = new JComboBox<>( model );
        combo.setRenderer( new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                final Component result = super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
                final IProject p = (IProject) value;
                final File file = p.getConfiguration().getBaseDir();
                setText( p.getConfiguration().getProjectName() + " [ " + file.getAbsolutePath() + " ]" );
                return result;
            }
        });

        final GridBagLayoutHelper cnstrs = GridBagLayoutHelper.newConstraints( 0, 0, 1, 1 );

        getContentPane().setLayout( new GridBagLayout() );
        getContentPane().add( new JLabel("Projects"), cnstrs.build() );
        getContentPane().add( combo, cnstrs.reset().pos(0,1).size( 1,1 ).build() );

        final JButton selectButton = new JButton("Select");
        selectButton.addActionListener( ev -> {
            if( combo.getSelectedItem() != null )
            {
                setVisible( false );
                dispose();
            }
        });
        getContentPane().add( selectButton, cnstrs.reset().pos(0,2).fixedSize().build() );
    }
}
