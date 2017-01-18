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
