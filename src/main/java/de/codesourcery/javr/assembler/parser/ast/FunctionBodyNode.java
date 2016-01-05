package de.codesourcery.javr.assembler.parser.ast;

public class FunctionBodyNode extends ASTNode 
{
    @Override
    protected FunctionBodyNode createCopy() {
        return new FunctionBodyNode();
    }
}