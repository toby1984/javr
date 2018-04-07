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
package de.codesourcery.javr.assembler.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StringResource implements Resource {

    private final String encoding = "UTF8";
    
    private final InMemoryResource res;
    
    public StringResource(String name,String value) 
    {
        res = new InMemoryResource(name,encoding);
        
        try ( OutputStream out = res.createOutputStream(); ) 
        {
            out.write( value.getBytes() ); 
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public String getName() {
        return res.getName();
    }
    
    @Override
    public boolean pointsToSameData(Resource other) {
        return other == this;
    }
    
    @Override
    public InputStream createInputStream() throws IOException {
        return res.createInputStream();
    }

    @Override
    public OutputStream createOutputStream() throws IOException {
        return res.createOutputStream();
    }

    @Override
    public int size() {
        return res.size();
    }

    @Override
    public String toString() {
        return res.toString()+" (String)";
    }
    
    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public String contentHash() {
        return res.contentHash();
    }
    
    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public void delete() throws IOException {
        res.delete();
    }
}