package de.codesourcery.javr.assembler.util;

import org.apache.commons.lang3.Validate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InputStreamResource implements Resource
{
    private final StreamSupplier in;
    private final String encoding;

    public interface StreamSupplier {
        InputStream get() throws IOException;
    }

    public InputStreamResource(StreamSupplier in,String encoding) {
        this.encoding = encoding;
        Validate.notNull( in, "in must not be null" );
        Validate.notBlank( encoding, "encoding must not be null or blank");
        this.in = in;
    }

    @Override
    public InputStream createInputStream() throws IOException
    {
        return in.get();
    }

    @Override
    public OutputStream createOutputStream() throws IOException
    {
        throw new UnsupportedOperationException( "Not supported for "+this );
    }

    @Override
    public boolean pointsToSameData(Resource other)
    {
        if ( other instanceof InputStreamResource) {
            return this.in == ((InputStreamResource) other).in;
        }
        return false;
    }

    @Override
    public int size()
    {
        throw new UnsupportedOperationException( "size() not implemented" );
    }

    @Override
    public boolean exists()
    {
        return true;
    }

    @Override
    public String getEncoding()
    {
        return encoding;
    }

    @Override
    public void delete()
    {
        throw new UnsupportedOperationException( "delete() not supported" );
    }

    @Override
    public String getName()
    {
        return "InputStreamResource";
    }
}