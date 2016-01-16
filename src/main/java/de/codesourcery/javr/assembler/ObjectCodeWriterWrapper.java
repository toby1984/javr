/**
 * Copyright 2015 Tobias Gierke <tobias.gierke@code-sourcery.de>
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
package de.codesourcery.javr.assembler;

import java.io.IOException;

import org.apache.commons.lang3.Validate;

/**
 * Adapter class to make implementing delegating {@link IObjectCodeWriter} 
 * implementations easier.
 * 
 * All methods just invoke the delegate. 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class ObjectCodeWriterWrapper implements IObjectCodeWriter {

    protected final IObjectCodeWriter delegate;

    public ObjectCodeWriterWrapper(IObjectCodeWriter delegate) {
        Validate.notNull(delegate, "delegate must not be NULL");
        this.delegate = delegate;
    }
    
    @Override
    public void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public Segment getCurrentSegment() {
        return delegate.getCurrentSegment();
    }

    @Override
    public void setCurrentSegment(Segment segment) {
        delegate.setCurrentSegment( segment );
    }

    @Override
    public void setStartAddress(Address address) {
        delegate.setStartAddress(address);
    }

    @Override
    public void allocateBytes(int num) {
        delegate.allocateBytes(num);
    }

    @Override
    public void writeByte(int data) {
        delegate.writeByte(data);
    }

    @Override
    public void writeWord(int data) {
        delegate.writeWord(data);
    }

    @Override
    public void finish(boolean success) throws IOException {
        delegate.finish(success);
    }

    @Override
    public Address getStartAddress(Segment segment) {
        return delegate.getStartAddress(segment);
    }

    @Override
    public int getCurrentByteAddress() {
        return delegate.getCurrentByteAddress();
    }

    @Override
    public Address getCurrentAddress() {
        return delegate.getCurrentAddress();
    }
}