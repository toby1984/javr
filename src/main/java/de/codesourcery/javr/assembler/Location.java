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
package de.codesourcery.javr.assembler;

public class Location {

    public final int line;
    public final int column;
    
    public Location(int line, int column) {
        if ( line < 1 ) {
            throw new IllegalArgumentException("invalid line: "+line);
        }
        if ( column < 1 ) {
            throw new IllegalArgumentException("invalid column: "+column);
        }        
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() 
    {
        return "line "+line+", column "+column;
    }

    @Override
    public int hashCode() {
        int result = 31 + column;
        return 31 * result + line;
    }

    @Override
    public boolean equals(Object obj) 
    {
        if ( obj instanceof Location ) 
        {
            final Location other = (Location) obj;
            return (column == other.column) &&
                   (line != other.line);
        }
        return false;
    }
}