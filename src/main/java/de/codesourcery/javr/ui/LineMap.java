/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Lexer;
import de.codesourcery.javr.assembler.parser.Location;
import de.codesourcery.javr.assembler.parser.Scanner;
import de.codesourcery.javr.assembler.parser.TextRegion;
import de.codesourcery.javr.assembler.parser.Token;
import de.codesourcery.javr.assembler.parser.TokenType;

public class LineMap 
{
    private final List<LineInfo> lines = new ArrayList<>();

    protected static final class LineInfo 
    {
        public final int line;
        public final int startOffset;
        public final int endOffset;
        
        public LineInfo(int line, int startOffset, int endOffset) 
        {
            if ( line < 1 ) {
                throw new IllegalArgumentException("invalid line: "+line);
            }
            if ( startOffset < 0 ) {
                throw new IllegalArgumentException("invalid startOffset: "+startOffset);
            }
            if ( endOffset < 0 ) {
                throw new IllegalArgumentException("invalid endOffset: "+endOffset);
            }
            this.line = line;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }
        
        @Override
        public String toString() {
            return "line "+line+" : "+startOffset+" - "+endOffset;
        }
        
        public Location getLocation(TextRegion r) {
            
            if ( startOffset <= r.start() && r.start() < endOffset ) 
            {
                return new Location( line , r.start() - startOffset +1 );
            }
            return null;
        }
    }
    
    public LineMap(String source,IConfigProvider provider) 
    {
        Validate.notNull(provider, "provider must not be NULL");
        
        if ( source != null ) 
        {
            final Scanner scanner = new Scanner( source );
            final Lexer lexer = provider.getConfig().createLexer( scanner );
            int offset = 0;
            int line = 1;
            boolean fileEndsWithEOL = false;
            while ( ! lexer.eof() ) 
            {
              while ( ! lexer.eof() && ! lexer.peek( TokenType.EOL ) ) {
                  lexer.next();
              }
              if ( lexer.peek( TokenType.EOL ) ) 
              {
                final Token eol = lexer.next();
                fileEndsWithEOL = lexer.eof();
                add( new LineInfo( line , offset , eol.offset ) );
                offset = eol.offset+1;
                line++;
              }
            }
            if ( ! fileEndsWithEOL ) {
                add( new LineInfo( line , offset , scanner.offset() ) );
            }
        }
        lines.sort( (a,b) -> Integer.compare( a.line , b.line ) );
    }    
    
    public int getLineCount() {
        return lines.size();
    }
    
    private void add(LineInfo l) 
    {
        lines.add( l );
    }
    
    public Location getLocationFor(TextRegion r) 
    {
        Validate.notNull(r, "region must not be NULL");
        
        Location result = null;
        for ( int i = 0 , len = lines.size() ; result == null && i < len ; i++ ) 
        {
            result = lines.get(i).getLocation( r );
        }
        return result;
    }

    public int getPositionOfLine(int lineNo) 
    {
        if ( lineNo < 1 ) {
            throw new IllegalArgumentException("line number must be >= 1 ");
        }
        lineNo--;
        if ( lineNo < lines.size() ) {
            return lines.get(lineNo).startOffset;
        }
        return -1;
    }    
}
