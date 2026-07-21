package io.quarkiverse.flow.dsl.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;

import io.serverlessworkflow.impl.marshaller.CustomObjectMarshaller;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;

public class SerializableObjectMarshaller implements CustomObjectMarshaller<Serializable> {

    @Override
    public void write(WorkflowOutputBuffer buffer, Serializable object) {
        try (ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bytesOut)) {
            out.writeObject(object);
            buffer.writeBytes(bytesOut.toByteArray());
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    @Override
    public Serializable read(WorkflowInputBuffer buffer, Class<? extends Serializable> objectClass) {
        try (ByteArrayInputStream bytesIn = new ByteArrayInputStream(buffer.readBytes());
                ObjectInputStream in = new ObjectInputStream(bytesIn)) {
            return objectClass.cast(in.readObject());
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public Class<Serializable> getObjectClass() {
        return Serializable.class;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}
