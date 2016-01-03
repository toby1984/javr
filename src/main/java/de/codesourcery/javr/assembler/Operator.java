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

import java.util.ArrayList;
import java.util.List;

public enum Operator 
{
    PLUS("+"),MINUS("-"),TIMES("*"),DIVIDE("/"),EQUALS("=");
    
    private final String symbol;

    private Operator(String symbol) {
        this.symbol = symbol;
    }
    
    public static List<Operator> getCandidates(String prefix) 
    {
        final List<Operator> result = new ArrayList<>();
        final Operator[] values = values();
        for (int i = 0 , len = values.length ; i < len ; i++) {
            Operator op = values[i];
            if ( op.symbol.startsWith( prefix ) ) {
                result.add( op );
            }
        }
        return result;
    }
    
    public static boolean isValidOperator(String s) 
    {
        final Operator[] values = values();        
        for (int i = 0 , len = values.length ; i < len ; i++) {
            Operator op = values[i];
            if ( op.symbol.equals( s ) ) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean isValidOperatorOrOperatorPrefix(char s) 
    {
        final Operator[] values = values();
        for (int i = 0 , len = values.length ; i < len ; i++) 
        {
            final Operator op = values[i];
            if ( op.symbol.charAt(0) == s ) {
                return true;
            }
        }
        return false;
    }        
}