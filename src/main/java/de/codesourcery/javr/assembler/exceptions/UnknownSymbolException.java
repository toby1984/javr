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
package de.codesourcery.javr.assembler.exceptions;

import org.apache.commons.lang3.Validate;

import de.codesourcery.javr.assembler.parser.Identifier;

public class UnknownSymbolException extends RuntimeException 
{
    private final Identifier name;

    public UnknownSymbolException(Identifier name) {
        super("Unknown symbol: '"+name+"'");
        Validate.notNull(name, "name must not be NULL");
        this.name = name;
    }

    public Identifier getName() {
        return name;
    }
}
