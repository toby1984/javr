package de.codesourcery.javr.ui.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.apache.log4j.Logger;

import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;

public class ApplicationConfigProvider implements IApplicationConfigProvider {

    private static final Logger LOG = Logger.getLogger(ApplicationConfigProvider.class);
    
    private IApplicationConfig applicationConfig = new ApplicationConfig();
    
    // @GuardedBy( listeners )
    private final List<Consumer<IApplicationConfig>> listeners = new ArrayList<>();
    
    public static IApplicationConfig load(InputStream in) throws IOException 
    {
        final String json;
        try ( BufferedReader reader = new BufferedReader( new InputStreamReader(in,"utf8" ) ) ) 
        {
            json = reader.lines().collect( Collectors.joining() );
        }
        return (IApplicationConfig) JsonReader.jsonToJava( json );
    }
    
    public static void save(IApplicationConfig config,OutputStream out) throws UnsupportedEncodingException, IOException 
    {
        final String json = JsonWriter.objectToJson(config);
        try ( BufferedWriter writer = new BufferedWriter( new OutputStreamWriter( out , "utf8" ) ) ) {
            writer.write( json );
        }
    }
    
    @Override
    public IApplicationConfig getApplicationConfig() {
        return applicationConfig;
    }

    @Override
    public void setApplicationConfig(IApplicationConfig config) 
    {
        this.applicationConfig = config.createCopy();
        
        final List<Consumer<IApplicationConfig>> copy; 
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }
        
        final IApplicationConfig configCopy = config.createCopy();
        for ( Consumer<IApplicationConfig> l : copy ) 
        {
            try {
                l.accept( configCopy );
            } 
            catch(Exception e) 
            {
                LOG.error("setApplicationConfig(): Listener "+l+" failed",e);
            }
        }
    }

    @Override
    public void addChangeListener(Consumer<IApplicationConfig> listener) 
    {
        Validate.notNull(listener, "listener must not be NULL");
        synchronized (listeners) {
            listeners.add( listener );
        }
    }

    @Override
    public void removeChangeListener(Consumer<IApplicationConfig> listener) 
    {
        Validate.notNull(listener, "listener must not be NULL");
        synchronized (listeners) {
            listeners.remove( listener );
        }
    }

}
