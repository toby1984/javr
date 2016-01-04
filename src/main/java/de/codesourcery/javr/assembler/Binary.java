package de.codesourcery.javr.assembler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.util.Resource;

public class Binary 
{
    private final Map<Segment,Resource> resources = new HashMap<>();
    private String identifier;
    
    public Binary(String identifier) 
    {
        Validate.notBlank(identifier, "identifier must not be NULL or blank");
        this.identifier = identifier;
    }
    
    public Optional<Resource> getResource(Segment s) 
    {
        Validate.notNull(s, "segment must not be NULL");
        return Optional.ofNullable( resources.get( s ) );
    }
    
    public void setResource(Segment s,Resource r) 
    {
        Validate.notNull(s, "s must not be NULL");
        Validate.notNull(r, "r must not be NULL");
        resources.put( s , r );
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    @Override
    public String toString() {
        return "Binary( "+identifier+" )";
    }
}
