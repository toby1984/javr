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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.lang3.Validate;

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

        /**
         * Returns the column number of a given offset on this line
         * @param offset
         * @return column number, first column is 1
         */
        public int columnNumber(int offset) {
            if ( offset > endOffset ) {
                throw new IllegalArgumentException( "Offset not inside this line's boundaries, offset: "+offset+", line: "+this );
            }
            return 1+(offset-startOffset);
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
                // skip over all newLines
                while ( ptr1 < len && isNewline( text.charAt( ptr1 ) ) )
                {
                    result.add( new Line( lineNum++, ptr0, ptr1 ) );
                    ptr0 = ptr1;
                    ptr1++;
                }
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

    public Optional<Line> getLineByOffset(int offset)
    {
        final Line[] endOffsetLine = {null};
        final int idx = binarySearch( line -> {
            if ( line.contains( offset ) )
            {
                return 0;
            } else if ( line.endOffset == offset ) {
                endOffsetLine[0] = line;
            }
            return Integer.compare( line.startOffset, offset );
        });
        if ( idx == -1 )
        {
            if ( endOffsetLine[0] != null ) {
                return Optional.of( endOffsetLine[0] );
            }
            return Optional.empty();
        }
        return Optional.of( lines[idx] );
    }
}