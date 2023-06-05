package de.codesourcery.javr.ui.config;

public class Model<T> implements IModel<T>
{
    public T value;

    public Model()
    {
    }

    public Model(T value)
    {
        this.value = value;
    }

    @Override
    public T getObject()
    {
        return value;
    }

    @Override
    public void setObject(T obj)
    {
        value = obj;
    }
}
