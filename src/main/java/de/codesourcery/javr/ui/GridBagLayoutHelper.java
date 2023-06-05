package de.codesourcery.javr.ui;

import java.awt.GridBagConstraints;
import java.awt.Insets;

public class GridBagLayoutHelper
{
    private GridBagConstraints cnstrs;

    public GridBagLayoutHelper()
    {
        this.cnstrs = createConstraints();
    }

    public GridBagLayoutHelper(int x, int y, int width, int height)
    {
        this.cnstrs = createConstraints( x, y, width, height );
    }

    public GridBagLayoutHelper pos(int x, int y) {
        cnstrs.gridx = x;
        cnstrs.gridy = y;
        return this;
    }

    public GridBagLayoutHelper size(int width, int height) {
        cnstrs.gridwidth = width;
        cnstrs.gridheight = height;
        return this;
    }

    private static GridBagConstraints createConstraints() {
        return createConstraints( 0, 0, 1, 1 );
    }

    private static GridBagConstraints createConstraints(int x, int y, int width, int height) {

        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.gridx = x;
        cnstrs.gridy = y;
        cnstrs.gridwidth = width;
        cnstrs.gridheight = height;
        cnstrs.weightx = 1.0;
        cnstrs.weighty = 1.0;
        return cnstrs;
    }

    public GridBagLayoutHelper reset() {
        cnstrs = createConstraints( );

        return this;
    }

    public GridBagLayoutHelper fill(int fill) {
        cnstrs.fill = fill;
        return this;
    }

    public GridBagLayoutHelper fixedSize() {
        cnstrs.fill = GridBagConstraints.NONE;
        cnstrs.weightx = 0;
        cnstrs.weighty = 0;
        return this;
    }

    public GridBagConstraints build()
    {
        final Insets insets = cnstrs.insets == null ? null : new Insets( cnstrs.insets.top, cnstrs.insets.left, cnstrs.insets.bottom, cnstrs.insets.right );
        return new GridBagConstraints(cnstrs.gridx, cnstrs.gridy, cnstrs.gridwidth,cnstrs.gridheight,
            cnstrs.weightx, cnstrs.weighty, cnstrs.anchor,cnstrs.fill, insets, cnstrs.ipadx, cnstrs.ipady);
    }


    public static GridBagLayoutHelper newConstraints(int x, int y, int width, int height) {
        return new GridBagLayoutHelper(x,y,width,height);
    }
}
