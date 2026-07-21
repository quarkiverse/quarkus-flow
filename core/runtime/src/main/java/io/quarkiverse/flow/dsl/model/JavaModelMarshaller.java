package io.quarkiverse.flow.dsl.model;

import io.serverlessworkflow.impl.marshaller.CustomObjectMarshaller;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;

public class JavaModelMarshaller implements CustomObjectMarshaller<JavaModel> {

    @Override
    public void write(WorkflowOutputBuffer buffer, JavaModel object) {
        buffer.writeObject(object.asJavaObject());
    }

    @Override
    public JavaModel read(WorkflowInputBuffer buffer, Class<? extends JavaModel> clazz) {
        return new JavaModel(buffer.readObject());
    }

    @Override
    public Class<JavaModel> getObjectClass() {
        return JavaModel.class;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
