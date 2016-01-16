package de.codesourcery.javr.ui.config;

public interface IModel<T> {

    public T getObject();
    
    public default void setObject(T obj) {
        throw new UnsupportedOperationException(this+" is a read-only model");
    }
}
