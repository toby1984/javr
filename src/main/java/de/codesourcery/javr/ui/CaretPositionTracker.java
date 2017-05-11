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
package de.codesourcery.javr.ui;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import de.codesourcery.javr.assembler.CompilationUnit;

public class CaretPositionTracker {

    private static final Logger LOG = Logger.getLogger(CaretPositionTracker.class);
    
    private List<CaretPosition> caretPositionHistory = new ArrayList<>();
    private int caretHistoryPtr = 0;
    
    public static final class CaretPosition 
    {
        public final int offset;
        public final CompilationUnit unit;
        
        private CaretPosition(int offset, CompilationUnit unit) 
        {
            this.offset = offset;
            this.unit = unit;
        }
        
        @Override
        public String toString() {
            return "CaretPosition "+offset+" , unit "+unit.getResource();
        }
    }
    
    public CaretPosition getPreviousCaretPosition() 
    {
        if ( caretHistoryPtr > 0 ) {
            return caretPositionHistory.get(--caretHistoryPtr); 
        }
        return null;
    }
    
    public CaretPosition getNextCaretPosition() 
    {
        if ( caretHistoryPtr < ( caretPositionHistory.size()-1 ) ) {
            return caretPositionHistory.get(++caretHistoryPtr); 
        }
        return null;
    }   
    
    public void discardCaretPositionHistory() {
        caretHistoryPtr = 0;
        caretPositionHistory.clear();
    }
    
    public void rememberCaretPosition(int position,CompilationUnit unit) 
    {
        final CaretPosition entry = new CaretPosition(position,unit);
        
        if ( ! caretPositionHistory.isEmpty() ) 
        {
            final CaretPosition latestEntry = caretPositionHistory.get( caretPositionHistory.size()-1 );
            final int delta = Math.abs( latestEntry.offset - position );
            if ( delta < 30 ) 
            {
                LOG.info("rememberCaretPosition(): IGNORING caret position "+entry+" , delta: "+delta);
                return;
            }
        }
        LOG.info("rememberCaretPosition(): Adding caret position "+entry+" at index "+caretHistoryPtr);
        caretPositionHistory.add( caretHistoryPtr , entry );
        if ( caretHistoryPtr < caretPositionHistory.size()-1 ) 
        {
            caretPositionHistory = new ArrayList<>( caretPositionHistory.subList( 0 , caretHistoryPtr+1 ) );
        }
    }
}
