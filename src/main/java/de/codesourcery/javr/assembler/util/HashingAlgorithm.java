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
package de.codesourcery.javr.assembler.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingAlgorithm 
{
    private static final char[] HEX_CHARS = new char[] { '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
    
    private final MessageDigest md;
    
    public HashingAlgorithm() 
    {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    public HashingAlgorithm update(byte[] data,int offset,int len) 
    {
        md.update( data , 0 , len );
        return this;
    }
    
    public String finish() {
        byte[] hash = md.digest();
        return toHexString( hash );
    }
    
    private static String toHexString(byte[] value) 
    {
        final StringBuilder result = new StringBuilder();
        for ( int i = 0 ; i < value.length ; i++ ) 
        {
            final int v = value[i];
            final char hi = HEX_CHARS[ (v & 0b11110000) >> 4 ];
            final char lo = HEX_CHARS[  v & 0b00001111 ];
            result.append( hi ).append( lo );
        }
        return result.toString();
    }
}
