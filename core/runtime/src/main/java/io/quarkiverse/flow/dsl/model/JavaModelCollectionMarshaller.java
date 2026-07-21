package io.quarkiverse.flow.dsl.model;

import java.util.Collection;

import io.serverlessworkflow.impl.marshaller.CustomObjectMarshaller;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;

public class JavaModelCollectionMarshaller implements CustomObjectMarshaller<JavaModelCollection> {

    @Override
    public void write(WorkflowOutputBuffer buffer, JavaModelCollection object) {
        buffer.writeObject(object.asJavaObject());
    }

    @Override
    public JavaModelCollection read(
            WorkflowInputBuffer buffer, Class<? extends JavaModelCollection> clazz) {
        return new JavaModelCollection((Collection<?>) buffer.readObject());
    }

    @Override
    public Class<JavaModelCollection> getObjectClass() {
        return JavaModelCollection.class;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
