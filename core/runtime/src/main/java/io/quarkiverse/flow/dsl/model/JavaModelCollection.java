package io.quarkiverse.flow.dsl.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.serverlessworkflow.impl.CollectionConversionUtils;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowModelCollection;

@SuppressWarnings("unchecked")
public class JavaModelCollection implements Collection<WorkflowModel>, WorkflowModelCollection {

    protected final Collection object;

    protected JavaModelCollection() {
        this.object = new ArrayList<>();
    }

    protected JavaModelCollection(Collection<?> object) {
        this.object = (Collection) JavaModel.asJavaObject(object);
    }

    @Override
    public int size() {
        return object.size();
    }

    @Override
    public boolean isEmpty() {
        return object.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("contains() is not supported yet");
    }

    private class ModelIterator implements Iterator<WorkflowModel> {

        private Iterator<?> wrapped;

        public ModelIterator(Iterator<?> wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public boolean hasNext() {
            return wrapped.hasNext();
        }

        @Override
        public WorkflowModel next() {
            Object obj = wrapped.next();
            return obj instanceof WorkflowModel value ? value : nextItem(obj);
        }
    }

    protected WorkflowModel nextItem(Object obj) {
        return new JavaModel(obj);
    }

    @Override
    public Iterator<WorkflowModel> iterator() {
        return new ModelIterator(object.iterator());
    }

    private List<WorkflowModel> toModelList() {
        List<WorkflowModel> models = new ArrayList<>(object.size());
        for (Object obj : object)
            models.add(obj instanceof WorkflowModel value ? value : nextItem(obj));

        return models;
    }

    @Override
    public Object[] toArray() {
        return toModelList().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return toModelList().toArray(a);
    }

    @Override
    public boolean add(WorkflowModel e) {
        return object.add(e.asJavaObject());
    }

    @Override
    public boolean remove(Object o) {
        return object.remove(((WorkflowModel) o).asJavaObject());
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("containsAll is not supported yet");
    }

    @Override
    public boolean addAll(Collection<? extends WorkflowModel> c) {
        int size = size();
        c.forEach(this::add);
        return size() > size;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        int size = size();
        c.forEach(this::remove);
        return size() < size;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("retainAll() is not supported yet");
    }

    @Override
    public void clear() {
        object.clear();
    }

    @Override
    public Object asJavaObject() {
        return object;
    }

    @Override
    public Class<?> objectClass() {
        return object.getClass();
    }

    @Override
    public <T> Optional<T> as(Class<T> clazz) {
        if (object == null)
            return Optional.empty();

        if (clazz.isInstance(this))
            return Optional.of(clazz.cast(this));
        if (clazz.isInstance(object))
            return Optional.of(clazz.cast(object));

        return CollectionConversionUtils.as(object, clazz);
    }

    @Override
    public int hashCode() {
        return Objects.hash(object);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof JavaModelCollection))
            return false;
        JavaModelCollection other = (JavaModelCollection) obj;
        return Objects.equals(object, other.object);
    }
}
