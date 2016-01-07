package de.codesourcery.javr.assembler.parser.ast;

public class FunctionBodyNode extends AbstractASTNode 
{
    @Override
    protected FunctionBodyNode createCopy() {
        return new FunctionBodyNode();
    }
}