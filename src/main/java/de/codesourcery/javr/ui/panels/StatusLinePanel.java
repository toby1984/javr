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
package de.codesourcery.javr.ui.panels;

import java.awt.Graphics;
import java.util.Optional;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import de.codesourcery.javr.ui.SourceMap;

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
