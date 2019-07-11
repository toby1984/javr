/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.ui.config;

import java.awt.Color;
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

import de.codesourcery.javr.ui.EditorSettings;
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
        final IApplicationConfig result = (IApplicationConfig) JsonReader.jsonToJava( json );

        // TODO: Hack -- brute-force fix colors that are hardly readable
        LOG.error( "load(): ---------------------------------------" );
        LOG.error( "load(): Overriding colors for LABEL and COMMENT" );
        LOG.error( "load(): ---------------------------------------" );
        final EditorSettings settings = result.getEditorSettings();
        settings.setColor(EditorSettings.SourceElement.LABEL , new Color( 0x039b38) );
        settings.setColor(EditorSettings.SourceElement.COMMENT, new Color( 0xffb200) );
        result.setEditorSettings( settings );
        // TODO: End hack
        return result;
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
