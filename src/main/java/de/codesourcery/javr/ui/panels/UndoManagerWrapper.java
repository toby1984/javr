package de.codesourcery.javr.ui.panels;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class UndoManagerWrapper
{
    private static final Logger LOG = LogManager.getLogger( UndoManagerWrapper.class.getName() );

    private static final IntFieldAccess<AbstractDocument.DefaultDocumentEvent> EVENT_OFFSET = new IntFieldAccess<>( AbstractDocument.DefaultDocumentEvent .class, "offset" );
    private static final IntFieldAccess<AbstractDocument.DefaultDocumentEvent> EVENT_LENGTH = new IntFieldAccess<>( AbstractDocument.DefaultDocumentEvent.class, "length" );
    private static final ObjectFieldAccess<UndoableEdit,AbstractDocument.DefaultDocumentEvent> DOCUMENT_EVENT =  new ObjectFieldAccess<>( "dde" );

    private static final Duration EVENT_FLUSH_TIMEOUT_MILLIS = Duration.ofMillis( 300 );

    private final class Protected
    {
        // @GuardedBy( this )
        private List<UndoableEditEvent> buffer = new ArrayList<>();

        // @GuardedBy( this )
        private int eventPointer = 0;

        // @GuardedBy( this )
        private int awakeCounter;
    }

    private final class BufferFlusher extends Thread
    {
        public BufferFlusher()
        {
            setName( "edit-event-buffer-flusher" );
            setDaemon( true );
        }

        public void wakeup()
        {
            synchronized( data )
            {
                data.awakeCounter++;
                data.notifyAll();
            }
        }

        public void run() {

            while ( true )
            {
                int wakeCounter;
                try
                {
                    synchronized(data)
                    {
                        // wait until awakeCounter is non-zero
                        wakeCounter = data.awakeCounter;
                        while ( wakeCounter == 0 ) {
                            data.wait();
                            wakeCounter = data.awakeCounter;
                        }

                        // awakeCounter is now non-zero,
                        // wait for timeout to elapse
                        data.wait( EVENT_FLUSH_TIMEOUT_MILLIS.toMillis() );

                        if ( data.awakeCounter != wakeCounter ) {
                            // we woke up but the wake counter got changed in the meantime
                            // (we got woken up by wakeup()) , start over
                            continue;
                        }

                        if ( data.buffer.isEmpty() )
                        {
                            data.awakeCounter = 0;
                            continue;
                        }
                    }
                }
                catch(Exception e)
                {
                    continue;
                }

                try
                {
                    SwingUtilities.invokeAndWait( () ->
                    {
                        UndoManagerWrapper.this.flushBuffer();
                        synchronized( data )
                        {
                            if ( data.awakeCounter > 0 )
                            {
                                data.awakeCounter--;
                            }
                        }
                    });
                }
                catch(Exception e)
                {
                    LOG.error("run(): Caught ",e);
                }
            }
        }
    }

    private static abstract class FieldAccess<CLAZZ>
    {
        protected final String fieldName;
        private Field field;

        public FieldAccess(String fieldName) {
            this.fieldName = fieldName;
        }

        protected final Field getField(Object target)
        {
            if ( field == null ) {
                field = getField( target.getClass() , fieldName );
            }
            return field;
        }

        public FieldAccess(Class<CLAZZ> clazz, String fieldName)
        {
            this.fieldName = fieldName;
            this.field = getField(clazz,fieldName);
        }

        private static Field getField(Class<?> clazz,String fieldName)
        {
            try
            {
                Field field = clazz.getDeclaredField( fieldName );
                field.setAccessible( true );
                return field;
            }
            catch (NoSuchFieldException e)
            {
                throw new RuntimeException( "Failed to access field '"+fieldName+"' in class "+clazz.getName(),e );
            }
        }

    }

    private static final class IntFieldAccess<CLAZZ> extends FieldAccess<CLAZZ>
    {
        public IntFieldAccess(String fieldName) {
            super(fieldName);
        }

        public IntFieldAccess(Class<CLAZZ> clazz, String fieldName) {
            super(clazz,fieldName);
        }

        public void set(CLAZZ object, int value)
        {
            final Field field = getField(object);
            try
            {
                field.setInt( object, value );
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException("Failed to access field "+field,e);
            }
        }

        public int get(CLAZZ object)
        {
            final Field field = getField(object);
            try
            {
                return field.getInt( object );
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException("Failed to access field "+field,e);
            }
        }
    }

    private static final class ObjectFieldAccess<CLAZZ,RESULT_TYPE> extends FieldAccess<CLAZZ>
    {
        public ObjectFieldAccess(String fieldName) {
            super(fieldName);
        }

        public ObjectFieldAccess(Class<CLAZZ> clazz, String fieldName) {
            super( clazz, fieldName );
        }

        public void set(CLAZZ object, RESULT_TYPE value)
        {
            final Field field = getField(object);
            try
            {
                field.set( object, value );
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException("Failed to access field "+field,e);
            }
        }

        public RESULT_TYPE get(CLAZZ object)
        {
            final Field field = getField(object);
            try
            {
                return (RESULT_TYPE) field.get( object );
            }
            catch (IllegalAccessException e)
            {
                throw new RuntimeException("Failed to access field "+field,e);
            }
        }
    }

    private final BufferFlusher thread;
    private final UndoManager undoManager = new UndoManager();
    private final Protected data = new Protected();

    public UndoManagerWrapper()
    {
        thread = new BufferFlusher();
        thread.start();
    }

    public boolean canUndo()
    {
        synchronized( data )
        {
            if ( ! data.buffer.isEmpty() )
            {
                return data.eventPointer - 1 >= 0 && edit( data.eventPointer - 1 ).canUndo();
            }
        }
        return undoManager.canUndo();
    }

    public boolean canRedo()
    {
        synchronized( data )
        {
            if ( ! data.buffer.isEmpty() )
            {
                return data.eventPointer + 1 < data.buffer.size() && edit( data.eventPointer + 1 ).canRedo();
            }
        }
        return undoManager.canRedo();
    }

    private UndoableEdit edit(int idx) {
        return event( idx ).getEdit();
    }

    private UndoableEditEvent event(int idx) {
        synchronized(data)
        {
            return data.buffer.get( idx );
        }
    }

    public void undo()
    {
        if ( !canUndo() )
        {
            return;
        }

        synchronized (data)
        {
            if ( ! data.buffer.isEmpty() )
            {
                data.eventPointer--;
                edit( data.eventPointer ).undo();
                return;
            }
        }
        undoManager.undo();
    }

    public void redo()
    {
        if ( !canRedo() )
        {
            return;
        }

        synchronized (data)
        {
            if ( ! data.buffer.isEmpty() )
            {
                data.eventPointer++;
                edit( data.eventPointer ).redo();
                return;
            }
        }
        undoManager.redo();
    }

    public void discardAllEdits()
    {
        clearBuffer();
        undoManager.discardAllEdits();
    }

    private void clearBuffer()
    {
        synchronized (data)
        {
            data.buffer.clear();
            data.eventPointer = 0;
            data.awakeCounter = 0;
        }
    }

    private void flushBuffer()
    {
        final List<UndoableEditEvent> toFlush; // copy so we don't have to call UndoManager#undoableEditHappened() while holding the lock
        synchronized (data)
        {
            final int bufferSize = data.buffer.size();
            toFlush = new ArrayList<>( bufferSize );

            System.out.println( "*** Flushing " + bufferSize + " edit events");

            // mark first edit in buffer as significant
            // and all over events as insignificant so
            // an undo goes back to the first one
            UndoableEditEvent current = data.buffer.get( 0 );
            if ( !current.getEdit().isSignificant() )
            {
                System.out.println( "Making event #0 significant" );
                current = makeSignificant( current, true );
            }
            toFlush.add( current );

            for (int i = 1; i < bufferSize; i++)
            {
                current = data.buffer.get( i );
                if ( current.getEdit().isSignificant() )
                {
                    System.out.println( "Making event #" + i + " INSIGNIFICANT" );
                    current = makeSignificant( current, false );
                }
                toFlush.add( current );
            }
            clearBuffer();
        }
        // call undo manager without holding the lock
        toFlush.forEach( undoManager::undoableEditHappened );
    }

    private boolean mergeEvents(UndoableEditEvent source, UndoableEditEvent target)
    {
        // TODO: Make this method work ...
        final AbstractDocument.DefaultDocumentEvent srcEvent = unwrap( source );
        final AbstractDocument.DefaultDocumentEvent targetEvent = unwrap( target );

        final DocumentEvent.EventType type = srcEvent.getType();
        if ( type == targetEvent.getType() )
        {
            final int start1 = getOffset( srcEvent );
            final int end1 = start1+ getLength( srcEvent );

            final int start2 = getOffset( targetEvent );
            final int end2 = start2 + getLength( targetEvent );

            final boolean adjacentRegions = (start2 == end1 || start1 == end2 );
            if ( adjacentRegions )
            {
                AbstractDocument.DefaultDocumentEvent left;
                AbstractDocument.DefaultDocumentEvent right;
                if ( start1 <= start2 ) {
                    left = srcEvent;
                    right = targetEvent;
                } else {
                    left = targetEvent;
                    right = srcEvent;
                }

                if ( type == DocumentEvent.EventType.CHANGE )
                {

                }
                else if ( type == DocumentEvent.EventType.INSERT )
                {

                }
                else if ( type == DocumentEvent.EventType.REMOVE )
                {

                }
                else
                {
                    LOG.warn( "mergeEvents(): Don't know how to merge " + type );
                    return false; // unknown event type
                }
                return true;
            }
        }
        return false;
    }

    private void setOffset(AbstractDocument.DefaultDocumentEvent ev, int offset)
    {
        EVENT_OFFSET.set(ev,offset);
    }

    private int getOffset(AbstractDocument.DefaultDocumentEvent ev) {
        return EVENT_OFFSET.get( ev );
    }

    private void setLength(AbstractDocument.DefaultDocumentEvent ev, int len) {
        EVENT_LENGTH.set(ev, len );
    }

    private int getLength(AbstractDocument.DefaultDocumentEvent ev) {
        return EVENT_LENGTH.get( ev );
    }

    public void undoableEditHappened(UndoableEditEvent e)
    {
        final long timestamp = System.currentTimeMillis();

        final UndoableEdit edit = e.getEdit();
        final String isSignificant = edit.isSignificant() ? "significant" : "insignificant";

        synchronized( data )
        {
            System.out.println( "#### RECEIVED: " + isSignificant + " event " + edit.getPresentationName() + " ( can redo: " + edit.canRedo() + " / can undo: " + edit.canUndo() + " - " + toString( unwrap( e ) ) );
            data.buffer.add( data.eventPointer, e );
            data.eventPointer++;
        }
        thread.wakeup(); // start timeout to flush event
    }

    private UndoableEdit proxy(UndoableEdit src,boolean isSignificant) {

        final InvocationHandler handler = (proxy, method, args) ->
        {
            if ( ! method.getName().equals( "isSignificant" ) ) {
                return method.invoke( src, args );
            }
            return isSignificant;
        };

        return (UndoableEdit) Proxy.newProxyInstance( getClass().getClassLoader(), new Class[]{ UndoableEdit.class } , handler );
    }

    private UndoableEditEvent makeSignificant(UndoableEditEvent e,boolean significant) {

        if ( e.getEdit().isSignificant() == significant ) {
            return e;
        }
        if ( e.getEdit().isSignificant() )
        {
            final UndoableEdit wrappedEdit = proxy( e.getEdit(), significant );
            return new UndoableEditEvent( e.getSource() , wrappedEdit );
        }
        return e;
    }

    private AbstractDocument.DefaultDocumentEvent unwrap(UndoableEditEvent e)
    {
        return DOCUMENT_EVENT.get( e.getEdit() );
    }

    private String toString(AbstractDocument.DefaultDocumentEvent event)
    {
        final int length = event.getLength();
        final int offset = event.getOffset();
        final DocumentEvent.EventType type = event.getType();
        return type+" @ "+offset+", len "+length;
    }
}