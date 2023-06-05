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
        public int offset;
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

    private CaretPosition top()
    {
        return caretPositionHistory.isEmpty() ? null : caretPositionHistory.get( caretPositionHistory.size()-1 );
    }
    
    public void rememberCaretPosition(int position,CompilationUnit unit) 
    {
        final CaretPosition lastAdded = top();
        if ( lastAdded != null && lastAdded.unit.hasSameResourceAs(  unit ) )
        {
            final int delta = Math.abs( lastAdded.offset - position );
            if ( delta < 30 )
            {
                LOG.info("rememberCaretPosition(): IGNORING caret position "+lastAdded+" , delta: "+delta);
                return;
            }
        }
        final CaretPosition newEntry = new CaretPosition( position, unit );

        LOG.info("rememberCaretPosition(): Adding caret position "+newEntry+" at index "+caretHistoryPtr);

        if ( caretPositionHistory.size() > 1 && caretHistoryPtr < caretPositionHistory.size() )
        {
            caretPositionHistory = new ArrayList<>( caretPositionHistory.subList( 0, caretHistoryPtr + 1 ) );
        }
        caretPositionHistory.add( newEntry );
        caretHistoryPtr = caretPositionHistory.size()-1;

    }
}
