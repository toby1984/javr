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
package de.codesourcery.javr.assembler.parser.ast;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.parser.Parser.CompilationMessage;
import de.codesourcery.javr.assembler.parser.Parser.Severity;

public class AST extends ASTNode 
{
    private static final Logger LOG = Logger.getLogger(AST.class);
    
    private final List<CompilationMessage> messages = new ArrayList<>();
    
    public void addMessage(CompilationMessage msg) 
    {
        Validate.notNull(msg, "msg must not be NULL");
        
        if ( LOG.isTraceEnabled() ) 
        { 
            LOG.trace("addMessage() "+msg.message , new Exception() );
        }
        this.messages.add(msg);
    }
    
    public List<CompilationMessage> getMessages() 
    {
        return new ArrayList<>( this.messages );
    }
    
    public boolean hasErrors() {
        return messages.stream().anyMatch( msg -> msg.severity == Severity.ERROR );
    }
    
    public boolean hasWarning() {
        return messages.stream().anyMatch( msg -> msg.severity == Severity.WARNING );
    }
    
    public boolean hasInfo() {
        return messages.stream().anyMatch( msg -> msg.severity == Severity.INFO );
    }

    @Override
    protected AST createCopy() {
        return new AST();
    }    
}