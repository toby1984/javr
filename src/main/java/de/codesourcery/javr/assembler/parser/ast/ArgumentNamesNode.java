package de.codesourcery.javr.assembler.parser.ast;

public class ArgumentNamesNode extends AbstractASTNode {

    public ArgumentNamesNode() {
        super();
    }

    @Override
    protected ASTNode createCopy() {
        return new ArgumentNamesNode();
    }
}