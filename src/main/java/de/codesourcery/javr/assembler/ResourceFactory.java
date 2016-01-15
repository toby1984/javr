package de.codesourcery.javr.assembler;

import java.io.IOException;

import de.codesourcery.javr.assembler.util.Resource;

/**
 * Responsible for locating {@link Resource}s.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public interface ResourceFactory 
{
    public Resource resolveResource(String child) throws IOException;
    
    public Resource resolveResource(Resource parent,String child) throws IOException;
}