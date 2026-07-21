package io.quarkiverse.flow.dsl.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.marshaller.DefaultBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;

class JavaModelSerializationTest {

    @Test
    void testSerializableJavaModel() {
        testMarshallUnMarshall(
                new JavaModel(new Person("Pepe Gotera", 32, new Address("Rue del Percebe", 13))));
    }

    @Test
    void testSerializableJavaModelCollection() {
        testMarshallUnMarshall(
                new JavaModelCollection(
                        List.of(new Person("Pepe Gotera", 32, new Address("Rue del Percebe", 13)))));
    }

    private void testMarshallUnMarshall(Object object) {
        WorkflowBufferFactory factory = DefaultBufferFactory.factory();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (WorkflowOutputBuffer writer = factory.output(output)) {
            writer.writeObject(object);
        }
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        try (WorkflowInputBuffer reader = factory.input(input)) {
            assertThat(reader.readObject()).isEqualTo(object);
        }
    }
}
