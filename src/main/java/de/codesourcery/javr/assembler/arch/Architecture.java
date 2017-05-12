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
package de.codesourcery.javr.assembler.arch;

import de.codesourcery.javr.assembler.arch.impl.ATMega328p;
import de.codesourcery.javr.assembler.arch.impl.ATMega88;
import de.codesourcery.javr.assembler.arch.impl.ATXmega;

/**
 * Enumeration of all supported architectures.
 *
 * @author tobias.gierke@code-sourcery.de
 * @see IArchitecture
 */
public enum Architecture 
{
    ATMEGA88("atmega88") {
        @Override
        public IArchitecture createImplementation() {
            return new ATMega88();
        }
    },
    ATMEGA328P("atmega328p") {
        @Override
        public IArchitecture createImplementation() {
            return new ATMega328p();
        }
    },
    XMEGA("xmega") {
        @Override
        public IArchitecture createImplementation() {
            return new ATXmega();
        }
    };
    
    private final String id;
    
    private Architecture(String id) {
        this.id = id;
    }

    public String getIdentifier() {
        return id;
    }
    
    public abstract IArchitecture createImplementation();
}
