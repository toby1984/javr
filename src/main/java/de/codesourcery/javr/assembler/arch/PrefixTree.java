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
package de.codesourcery.javr.assembler.arch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.arch.AbstractArchitecture.InstructionEncoding;

/**
 * A binary prefix tree used to look up instructions my bit patterns.
 * 
 *  TODO: Currently not used anymore...
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class PrefixTree 
{
    private final Node root = new Node("");
    
    private static final class Node 
    {
        public Node parent;
        public Node bitOne;
        public Node bitZero;
        public final List<InstructionEncoding> encodings = new ArrayList<>();
        public final String path;
        
        public Node(String path) {
            this.path = path;
        }
        
        @Override
        public String toString() {
            return toString(0);
        }
        
        public void add(InstructionEncoding enc) {
            
            Validate.notNull(enc, "encoding must not be NULL");
            this.encodings.add( enc );
            // decoding process will pick the encoding with the most specific (=largest number of matching bits)
            // pattern and fail if two candidate encodings end up having the same number of 
            // fixed bits in their masks 
            // sort encodings by 
            final Comparator<InstructionEncoding> cmp = (a,b) -> 
            {
                // sort descending by pattern length
                return Integer.compare( b.encoder.getOpcodeBitCount() , a.encoder.getOpcodeBitCount() );  
            };
            this.encodings.sort( cmp );
        }
        
        public String toString(int depth) 
        {
            String result = "";
            if ( bitOne != null ) {
                result += bitOne.toString( depth+1);
            }
            if ( bitZero != null ) {
                result += bitZero.toString( depth+1 );
            }            
            final String padding = StringUtils.repeat( "_" , depth );
            final String lines = ""+encodings.stream().map( s -> s.mnemonic ).collect(Collectors.joining(",") );
            return padding+path+": "+lines+"\n"+result;
        }
    }
    
    public String toString() 
    {
        return root.toString(0);
    }
    
    public void add(InstructionEncoding encoding) 
    {
        final String pattern = encoding.encoder.getTrimmedPattern();
        Node current = root;
        for ( int i = 0 , len = pattern.length() ; i < len ; i++ ) 
        {
            final char c = pattern.charAt(i);
            if ( c == '0' ) 
            {
                if ( current.bitZero != null ) {
                    current = current.bitZero;
                } 
                else 
                {
                    final Node n = new Node( pattern.substring(0,i+1) );
                    n.parent = current;
                    current.bitZero= n;
                    current = n;
                }
            } 
            else if ( c == '1' ) 
            {
                if ( current.bitOne != null ) {
                    current = current.bitOne;
                } 
                else 
                {
                    final Node n = new Node( pattern.substring(0,i+1) );
                    n.parent = current;
                    current.bitOne = n;
                    current = n;
                }
            } 
            else 
            {
                break;
            }
        }
//        System.out.println("Adding '"+encoding.mnemonic.toUpperCase()+" to "+current.path+" (trimmed: "+pattern+")");
        current.add( encoding );
    }
    
    public List<InstructionEncoding> getMatches(int value) 
    {
        Node current = root;
        int mask=0b10000000_00000000_00000000_00000000;
        for ( int i = 0 ; i < 32 ; i++ ) 
        {
            final boolean bitSet = (value & mask)!=0;
            if ( bitSet ) 
            {
                if ( current.bitOne == null ) { 
                    return pick( current , value );
                }
                current = current.bitOne;
            } else {
                if ( current.bitZero == null ) { 
                    return pick( current , value );
                }
                current = current.bitZero;
            }
            mask >>>= 1;
        }
        return null;
    }
    
    private List<InstructionEncoding> pick(Node node,int value) 
    {
        // look for matches
        final List<InstructionEncoding> result = new ArrayList<>();
        for ( InstructionEncoding enc : node.encodings ) 
        {
            if ( enc.encoder.matches( value ) ) 
            {
                result.add( enc );
            }
        }
        if ( result.isEmpty() && node.parent != null ) 
        {
            return pick(node.parent,value);
        }
        return result;
    }
}