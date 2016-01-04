package de.codesourcery.javr.assembler.exceptions;

import de.codesourcery.javr.assembler.parser.TextRegion;

public class BadExpressionException extends ParseException {

    public BadExpressionException(String msg, int offset) {
        super(msg, offset);
    }
    
    public BadExpressionException(String msg, TextRegion region) {
        super(msg, region.start());
    }    
}
