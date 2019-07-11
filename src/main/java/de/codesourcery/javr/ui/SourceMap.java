package de.codesourcery.javr.ui;

import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SourceMap
{
    public static final class Line
    {
        public int lineNum; // line number, first line has number 1
        public int startOffset; // start offset relative to start of text (inclusive)
        public int endOffset; // end offset of this line relative to start of text (exlusive), not covering any
        // newline characters

        public Line(int lineNum, int startOffset, int endOffset)
        {
            this.lineNum = lineNum;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public boolean contains(int offset)
        {
            return startOffset <= offset && offset < endOffset;
        }

        @Override
        public String toString()
        {
            return "Line no. " + lineNum + "(start=" + startOffset + ", end=" + endOffset + ")";
        }
    }

    private final Supplier<String> textSupplier;
    private Line[] lines = new Line[0];
    private boolean isValid;

    public SourceMap(Supplier<String> textSupplier)
    {
        Validate.notNull( textSupplier, "textSupplier must not be null" );
        this.textSupplier = textSupplier;
    }

    private interface LineToInt
    {
        int compare(Line line);
    }

    public void invalidate()
    {
        this.isValid = false;
    }

    private static boolean isNewline(char c)
    {
        return ( c == '\n' || c == '\r' );
    }

    /**
     * Visit all lines that contain text from a given offset range.
     *
     * @param startOffsetInclusive
     * @param endOffsetExclusive
     * @param consumer
     */
    public void visitLinesByOffset(int startOffsetInclusive, int endOffsetExclusive, Consumer<Line> consumer)
    {
        // find first line
        int idx = binarySearch( line -> {
            if ( line.contains( startOffsetInclusive ) )
            {
                return 0;
            }
            return Integer.compare( line.startOffset, startOffsetInclusive );
        } );

        if ( idx != -1 )
        {
            for (int i = idx, len = lines.length; i < len && lines[i].startOffset < endOffsetExclusive; i++)
            {
                consumer.accept( lines[i] );
            }
        }
    }

    private int binarySearch(LineToInt comp)
    {
        assertValid();

        int low = 0;
        int high = lines.length - 1;

        while ( low <= high )
        {
            int mid = ( low + high ) >>> 1;
            Line midVal = lines[mid];

            final int cmpResult = comp.compare( midVal );
            if ( cmpResult < 0 )
            {
                low = mid + 1;
            }
            else if ( cmpResult > 0 )
            {
                high = mid - 1;
            }
            else
            {
                return mid;
            }
        }
        return -1;
    }

    private void assertValid()
    {
        if ( !isValid )
        {
            recalculate();
        }
    }

    private void recalculate()
    {
        final String text = textSupplier.get();
        final List<Line> result = new ArrayList<>( this.lines.length );

        int ptr0 = 0;
        int ptr1 = 1;

        int lineNum = 1;
        for (final int len = text.length(); ptr1 < len; ptr1++)
        {
            char c = text.charAt( ptr1 );
            if ( isNewline( c ) )
            {
                result.add( new Line( lineNum++, ptr0, ptr1 ) );
                // advance to start of next line
                while ( ptr1 < len && isNewline( text.charAt( ptr1 ) ) )
                {
                    ptr1++;
                }
                ptr0 = ptr1;
            }
        }
        if ( ptr0 != ptr1 )
        {
            result.add( new Line( lineNum, ptr0, ptr1 ) );
        }
        this.lines = result.toArray( new Line[0] );
        this.isValid = true;
    }

    /**
     * @param lineNum line number, first line is number 1 (!!)
     * @return
     */
    public Optional<Line> getLineByNumber(int lineNum)
    {
        if ( lineNum < 1 )
        {
            throw new IllegalArgumentException( "Line number must be >= 1" );
        }
        final Line[] result = {null};
        binarySearch( line -> {
            final int cmp = Integer.compare( line.lineNum, lineNum );
            if ( cmp == 0 )
            {
                result[0] = line;
            }
            return cmp;
        } );
        return Optional.ofNullable( result[0] );
    }

    public Optional<Line> getLineByOffset(int startingOffset)
    {
        final int idx = binarySearch( line -> {
            if ( line.contains( startingOffset ) )
            {
                return 0;
            }
            return Integer.compare( line.startOffset, startingOffset );
        } );
        if ( idx == -1 )
        {
            return Optional.empty();
        }
        return Optional.of( lines[idx] );
    }
}