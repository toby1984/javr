package de.codesourcery.javr.ui.panels;

import de.codesourcery.javr.assembler.parser.Parser;
import de.codesourcery.javr.ui.SourceMap;
import de.codesourcery.javr.ui.config.IApplicationConfigProvider;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.ToolTipManager;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GutterPanel extends JPanel
{
    private static final Logger LOG = LogManager.getLogger( GutterPanel.class.getName() );

    private static final boolean DEBUG_RENDER_LINE_BOUNDARIES = false;

    private final JScrollPane scrollpane;
    private final EditorPanel editorPanel;

    private boolean displayLineNumbers;
    private int startingOffset;
    private int endOffset;

    private final List<GutterIcon> gutterIcons = new ArrayList<>();

    private final class GutterIcon {

        public final Rectangle area;
        public final int lineNum;

        private GutterIcon(Rectangle area, int lineNum)
        {
            this.area = area;
            this.lineNum = lineNum;
        }

        public List<Parser.CompilationMessage> getMessages()
        {
            final Optional<SourceMap.Line> line = editorPanel.getSourceMap().getLineByNumber( lineNum );
            if ( line.isPresent() )
            {
                final List<Parser.CompilationMessage> messages = editorPanel.getCompilationUnit().getMessages( false );
                return messages.stream().filter( msg -> msg.isWithinOffset( line.get().startOffset, line.get().endOffset ) ).collect( Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    public GutterPanel(JScrollPane scrollpane, EditorPanel editorPanel, IApplicationConfigProvider configProvider)
    {
        this.scrollpane = scrollpane;
        this.editorPanel = editorPanel;

        this.displayLineNumbers = configProvider.getApplicationConfig().getEditorSettings().isDisplayLineNumbers();
        configProvider.addChangeListener( newConfig -> this.displayLineNumbers = newConfig.getEditorSettings().isDisplayLineNumbers() );

        ToolTipManager.sharedInstance().setInitialDelay( 150 );
        ToolTipManager.sharedInstance().registerComponent( this );

        addMouseMotionListener( new MouseAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent e)
            {
                String tooltip = null;
                for ( GutterIcon icon : gutterIcons )
                {
                    if ( icon.area.contains(  e.getPoint() ) )
                    {
                        final List<Parser.CompilationMessage> messages = icon.getMessages();
                        if ( ! messages.isEmpty() ) {
                            tooltip = messages.stream().map( msg -> msg.severity+" - "+msg.message )
                                      .collect( Collectors.joining( "<br>", "<html><body>", "</body></html>"));
                        }
                    }
                }
                setToolTipText( tooltip );
            }
        });

        scrollpane.getVerticalScrollBar().addAdjustmentListener( new AdjustmentListener()
        {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e)
            {
                final JViewport viewport = scrollpane.getViewport();
                final JTextPane editor = (JTextPane) viewport.getView();

                final Point p0 = viewport.getViewPosition();
                final Point p1 = new Point( p0.x + viewport.getWidth(), p0.y + viewport.getHeight() );

                int startingOffset = editor.viewToModel2D( p0 );
                int endOffset = editor.viewToModel2D( p1 );

                final String text = editor.getText();
                if ( startingOffset >= 0 && endOffset >= 0 && startingOffset < text.length() && endOffset <= text.length() )
                {
                    GutterPanel.this.startingOffset = startingOffset;
                    GutterPanel.this.endOffset = endOffset;
                    GutterPanel.this.paintImmediately( 0,0,GutterPanel.this.getWidth(), GutterPanel.this.getHeight() );
                }
            }
        });
    }

    private BufferedImage image;
    private Graphics2D graphics;

    private Graphics2D createGraphics() {

        if (image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight() ) {
            if ( graphics != null ) {
                graphics.dispose();
            }
            image = new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);
            graphics = image.createGraphics();
        }
        return graphics;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        final Graphics2D gfx = createGraphics();

        gfx.setColor( Color.WHITE );
        gfx.fillRect( 0,0,getWidth(),getHeight() );

        final JTextPane editor = (JTextPane) scrollpane.getViewport().getView();
        final String text = editor.getText();

        // discard existing gutter icons so even if we
        // crash or cannot find the range (for whatever reason)
        // we don't show the wrong ones
        gutterIcons.clear();
        if ( startingOffset >= 0 && endOffset >= 0 && startingOffset < text.length() && endOffset <= text.length() )
        {
            final Optional<SourceMap.Line> firstLine = editorPanel.getSourceMap().getLineByOffset( startingOffset );

            firstLine.ifPresent( line -> {
                System.out.println("Line @ ( "+startingOffset+" - "+endOffset+" )  -> "+line);
            } );
            // TODO: the next check should not be necessary , fix the root cause !!
            if ( firstLine.isPresent() )
            {
                // find compilation messages for each line we're displaying on the gutter
                final Map<Integer, List<Parser.CompilationMessage>> messagesByLineNumber = new HashMap<>();

                final List<Parser.CompilationMessage> messages = editorPanel.getCompilationUnit().getMessages( false );

                final SourceMap sourceMap = editorPanel.getSourceMap();
                sourceMap.visitLinesByOffset( startingOffset, endOffset, line ->
                {
                    System.out.println("VISITING: "+line);
                    final List<Parser.CompilationMessage> list =
                    messages.stream().filter( msg -> msg.isWithinOffset( line.startOffset, line.endOffset ) ).collect( Collectors.toList() );
                    if ( !list.isEmpty() )
                    {
                        messagesByLineNumber.put( line.lineNum, list );
                    }
                });

                try
                {
                    // figure out (x,y) coordinates of start of first line in view
                    final Rectangle2D rectangle = editor.modelToView2D( firstLine.get().startOffset );
                    int textHeight = editor.getFontMetrics( editor.getFont() ).getHeight();
                    if ( rectangle.getHeight() > 0 && textHeight != (int) rectangle.getHeight() ) {
                        textHeight = (int) rectangle.getHeight();
                    }

                    // calculate actual Y starting offset based on viewport
                    double firstY = rectangle.getY();
                    final int viewportY = scrollpane.getViewport().getViewRect().y;
                    final int yOffset = (int) (firstY - viewportY);

                    // render gutter

                    // calculate the number of visible rows we need to render
                    final int viewportHeight = scrollpane.getViewport().getHeight();
                    final int visibleRows = (int) Math.ceil( (viewportHeight - yOffset ) / (float) textHeight );

                    gfx.setColor( Color.RED );
                    for (int rowNum = 0, lineNum = firstLine.get().lineNum , y = yOffset+textHeight ; rowNum < visibleRows; rowNum++, lineNum++, y += textHeight)
                    {
                        final List<Parser.CompilationMessage> onThisLine = messagesByLineNumber.get( lineNum );
                        if ( DEBUG_RENDER_LINE_BOUNDARIES )
                        {
                            gfx.drawLine( 0, y, getWidth(), y );
                        }

                        if ( onThisLine != null && ! onThisLine.isEmpty() )
                        {
                            // we got messages on this line

                            // Customize color of gutter icon based on most severe message
                            Parser.Severity worst = null;
                            for ( Parser.CompilationMessage msg : onThisLine ) {
                                if ( worst == null || msg.severity.level > worst.level ) {
                                    worst = msg.severity;
                                }
                            }
                            final Color color;
                            switch( worst )
                            {
                                case INFO:
                                    color = Color.BLUE;
                                    break;
                                case WARNING:
                                    color = Color.YELLOW;
                                    break;
                                case ATTENTION:
                                    color = Color.ORANGE;
                                    break;
                                case ERROR:
                                    color = Color.RED;
                                    break;
                                default:
                                    color = Color.RED;
                            }
                            gfx.setColor( color );
                            gfx.fillRect( 0,y, getWidth(), textHeight );
                            gutterIcons.add( new GutterIcon( new Rectangle( 0,y,getWidth(), textHeight) , lineNum ) );
                        }

                        gfx.setColor(Color.BLACK);
                        gfx.drawRect( 0,y, getWidth(), textHeight );

                        // render line number
                        if ( displayLineNumbers )
                        {
                            final int yText = 1+ (int) Math.ceil(y + textHeight/2.0f - gfx.getFontMetrics().getAscent());

                            final String lineNumString = Integer.toString( lineNum );
                            final Rectangle2D bounds = gfx.getFontMetrics().getStringBounds( lineNumString, gfx );
                            gfx.drawString( lineNumString, (int) Math.ceil( getWidth() - bounds.getWidth() ) , yText );
                        }
                    }
                }
                catch (BadLocationException e)
                {
                    // Should never happen
                    LOG.error("paint(): Caught ",e);
                }
            } else {
                LOG.error("paint(): Failed to find line for offset "+startingOffset+" ???");
            }
        }
        g.drawImage( image, 0 , 0 , null );
    }
}