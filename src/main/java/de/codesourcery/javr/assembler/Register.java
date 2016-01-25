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

import org.apache.commons.lang3.Validate;

/**
 * Value class that identifies a CPU register,also providing hints about the context (post-increment,pre-decrement) 
 * in which this register was mentioned.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class Register 
{
    //  X/Y/Z (r27:r26/r29:r28/r31:r30)
    public static final int REG_X = 26;
    public static final int REG_Y = 28;
    public static final int REG_Z = 30;
    
    private final String expression;
    private final boolean postIncrement;
    private final boolean preDecrement;
    
    public Register(String name,boolean postIncrement,boolean preDecrement) 
    {
        Validate.notBlank(name, "name must not be NULL or blank");
        this.expression = name.toLowerCase();
        this.postIncrement = postIncrement;
        this.preDecrement = preDecrement;
    }
    
    @Override
    public String toString() {
        return expression;
    }
    
    public boolean isPostIncrement() {
        return postIncrement;
    }
    
    public boolean isPreDecrement() 
    {
        return preDecrement;
    }    
    
    public boolean isCompoundRegister() 
    {
        switch(expression) {
            case "x":
            case "y":
            case "z":
                return true;
            default:
                return expression.contains(":");
        }
    }
    
    public String getRegisterName() {
        return this.expression;
    }
    
    public int getRegisterNumber() 
    {
        return getRegisterNumber( this.expression );
    }
    
    private int getRegisterNumber(String s) 
    {
        if ( s.contains(":") ) 
        {
            final String[] parts = s.split(":");
            int hi = getRegisterNumber(parts[0]);
            int lo = getRegisterNumber(parts[1]);
            final int delta = hi - lo;
            if ( delta != 1 ) {
                throw new RuntimeException("Adjacent registers ( Rd+1:Rd ) required");
            }
            return lo;
        }
        switch(s) 
        {
            case "x": return REG_X;
            case "y": return REG_Y;
            case "z": return REG_Z;
        }
        if ( s.startsWith("r" ) ) {
            return Integer.parseInt( s.substring(1) );
        }
        throw new RuntimeException("Invalid register expression: "+s);
    }
    
    @Override
    public boolean equals(Object obj) 
    {
        if ( obj instanceof Register) 
        {
            return this.expression.equals( ((Register) obj).expression );
        }
        return false;
    }
    
    @Override
    public int hashCode() 
    {
        return expression.toLowerCase().hashCode();
    }
}