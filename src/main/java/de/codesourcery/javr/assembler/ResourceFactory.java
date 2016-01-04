package de.codesourcery.javr.assembler;

import java.io.IOException;

import de.codesourcery.javr.assembler.util.Resource;

public interface ResourceFactory 
{
    public Resource getResource(Segment segment) throws IOException;
    
    public Resource resolveResource(Resource parent,String child) throws IOException;
}
