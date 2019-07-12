package de.codesourcery.javr.ui.panels;

import de.codesourcery.javr.ui.SourceMap;

import javax.swing.JPanel;
import javax.swing.JTextPane;
import java.awt.Graphics;
import java.util.Optional;

public class StatusLinePanel extends JPanel
{
    private final SourceMap sourceMap;
    private final JTextPane editor;
    private int caretPosition;

    public StatusLinePanel(SourceMap sourceMap,JTextPane editor) {
        this.sourceMap = sourceMap;

        this.editor = editor;
        editor.addCaretListener( e -> {
            caretPosition = e.getDot();
            paintImmediately( 0,0, getWidth(), getHeight() );
        });
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent( g );

        final Optional<SourceMap.Line> line = sourceMap.getLineByOffset( caretPosition );
        if ( line.isPresent() )
        {
            final SourceMap.Line l = line.get();
            final int height = g.getFontMetrics().getHeight();
            g.drawString("line "+l.lineNum+", column "+l.columnNumber( caretPosition )+", offset "+caretPosition,0 , height );
        }
    }
}
