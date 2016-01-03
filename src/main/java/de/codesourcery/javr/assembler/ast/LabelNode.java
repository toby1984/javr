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
package de.codesourcery.javr.assembler.ast;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.ICompilationContext;
import de.codesourcery.javr.assembler.ICompilationContext.Phase;
import de.codesourcery.javr.assembler.Identifier;
import de.codesourcery.javr.assembler.LabelSymbol;
import de.codesourcery.javr.assembler.TextRegion;
import de.codesourcery.javr.assembler.ast.SegmentNode.Segment;

public class LabelNode extends ASTNode 
{
    public final Identifier identifier;
    public Segment segment;
    public int address=-1;
    
    public LabelNode(Identifier id,TextRegion region) {
        super(region);
        Validate.notNull(id, "id must not be NULL");
        this.identifier= id;
    }
    
    @Override
    public void compile(ICompilationContext ctx) 
    {
        if ( ctx.isInPhase( Phase.GATHER_SYMBOLS ) ) 
        {
            ctx.getSymbolTable().defineSymbol( new LabelSymbol( this ) );
        } 
        else if ( ctx.isInPhase( Phase.RESOLVE_SYMBOLS ) ) 
        {
            final LabelSymbol symbol = (LabelSymbol) ctx.getSymbolTable().get( this.identifier );
            symbol.setAddress( ctx.currentAddress() );
        }
    }
    
    public void setSegment(Segment segment) {
        this.segment = segment;
    }
    
    public Segment getSegment() {
        return segment;
    }
    
    public void setAddress(int address) {
        this.address = address;
    }
    
    public int getAddress() {
        return address;
    }
    
    @Override
    public String getAsString() {
        return identifier.value+":";
    }
}