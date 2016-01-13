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

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilationUnit;
import de.codesourcery.javr.assembler.ICompilationContext;

public class AST extends AbstractASTNode implements Resolvable
{
    private static final Logger LOG = Logger.getLogger(AST.class);
    
    private CompilationUnit compilationUnit;
    
    @Override
    protected AST createCopy() {
        return new AST();
    }
    
    public void setCompilationUnit(CompilationUnit compilationUnit) 
    {
		Validate.notNull(compilationUnit, "compilationUnit must not be NULL");
		this.compilationUnit = compilationUnit;
	}
    
    @Override
    public CompilationUnit getCompilationUnit() {
    	return compilationUnit;
    }

	@Override
	public boolean resolve(ICompilationContext context) 
	{
		for ( ASTNode child : children() ) 
		{
			if ( child instanceof Resolvable) 
			{
				((Resolvable) child).resolve( context );
			}
		}
		return true;
	}
}