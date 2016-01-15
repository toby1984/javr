package de.codesourcery.javr.assembler.parser;

import de.codesourcery.javr.assembler.exceptions.BadExpressionException;
import de.codesourcery.javr.assembler.parser.ast.ASTNode;
import de.codesourcery.javr.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.javr.assembler.parser.ast.OperatorNode;

/**
 * Helper class used by the {@link ShuntingYard} implementation.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class ExpressionToken 
{
    private final TextRegion textRegion;
    private final ExpressionTokenType type;
    private final ASTNode token;

    public static enum ExpressionTokenType {
        FUNCTION,
        ARGUMENT_DELIMITER,
        OPERATOR,
        PARENS_OPEN,
        PARENS_CLOSE,
        EXPRESSION,
        VALUE;
    }

    public ExpressionToken(ExpressionTokenType type,TextRegion region)
    {
        this.type = type;
        this.token = null;
        this.textRegion = region;
    }

    public ExpressionToken(ExpressionTokenType type,Token token)
    {
        this.type = type;
        this.token = null;
        this.textRegion = token.region();
    }

    public ExpressionToken(ExpressionTokenType type, ASTNode token)
    {
        this.type = type;
        this.token = token;
        this.textRegion = token.getTextRegion();
    }

    public TextRegion getTextRegion()
    {
        return this.textRegion;
    }

    @Override
    public String toString()
    {
        switch( this.type ) {
            case ARGUMENT_DELIMITER:
                return ",";
            case FUNCTION:
                return ((FunctionCallNode) getNode()).functionName.getValue();
            case OPERATOR:
                return ((OperatorNode) getNode()).getOperatorType().toPrettyString();
            case PARENS_CLOSE:
                return ")";
            case PARENS_OPEN:
                return "(";
            case VALUE:
                return getNode().toString();
            default:
                return this.type+" | "+this.token;
        }
    }

    public boolean isArgumentDelimiter() {
        return hasType(ExpressionTokenType.ARGUMENT_DELIMITER);
    }

    public boolean isFunction() {
        return hasType(ExpressionTokenType.FUNCTION);
    }

    public boolean isOperator() {
        return hasType(ExpressionTokenType.OPERATOR);
    }

    public boolean hasAllOperands() {
        if ( ! isOperator() ) {
            throw new BadExpressionException("hasAllOperands() invoked on something that's not an operator: "+this , this.textRegion );
        }
        return ((OperatorNode) getNode()).hasAllOperands();
    }

    public boolean isLeftAssociative()
    {
        if ( ! isOperator() ) {
            throw new BadExpressionException("isLeftAssociative() invoked on something that's not an operator: "+this, this.textRegion);
        }
        return ((OperatorNode) getNode()).getOperatorType().isLeftAssociative();
    }

    public boolean isValue() {
        return hasType(ExpressionTokenType.VALUE);
    }

    public boolean isParens() {
        return isParensOpen() || isParensClose();
    }

    public boolean isParensOpen() {
        return hasType(ExpressionTokenType.PARENS_OPEN);
    }

    public boolean isParensClose() {
        return hasType(ExpressionTokenType.PARENS_CLOSE);
    }

    public ASTNode getNode()
    {
        return this.token;
    }

    public boolean hasType(ExpressionTokenType t) {
        return getType() == t;
    }

    public ExpressionTokenType getType()
    {
        return this.type;
    }
}