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
package de.codesourcery.javr.assembler.parser;

public class TextRegion {

    private int start;
    private int len;
    
    public TextRegion(int start, int len) 
    {
        if ( start < 0 ) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if ( len < 0 ) {
            throw new IllegalArgumentException("len must be >= 0");
        }        
        this.start = start;
        this.len = len;
    }
    
    @Override
    public int hashCode() {
        int result = 31 + len;
        return 31 * result + start;
    }
    
    public TextRegion incLength() {
        this.len += 1;
        return this;
    }

    @Override
    public boolean equals(Object obj) 
    {
        if ( obj instanceof TextRegion) {
            final TextRegion other = (TextRegion) obj;
            return this.start == other.start && this.len == other.len;
        }
        return false;
    }

    public TextRegion merge(Token tok) {
        return merge( tok.region() );
    }
    
    /**
     * 
     * @param other
     * @return this instance (for chaining)
     */
    public TextRegion merge(TextRegion other) {
        final int newStart = Math.min(this.start,other.start);
        final int newEnd = Math.max( this.end(), other.end() );
        this.start = newStart;
        this.len = newEnd-newStart;
        return this;
    }
    
    public int start() {
        return start;
    }
    
    public int length() {
        return len;
    }
    
    public int end() {
        return start+len;
    }

    @Override
    public String toString() 
    {
        return start()+"-"+end();
    }
}
