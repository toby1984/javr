package de.codesourcery.javr.assembler;

import de.codesourcery.javr.assembler.util.Resource;

public interface ResourceFactory 
{
    public Resource getResource(Binary binary,Segment segment);
}
