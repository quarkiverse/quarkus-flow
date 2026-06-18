package io.quarkiverse.flow.persistence.mvstore;

import java.nio.file.Files;
import java.nio.file.Path;

import org.h2.mvstore.MVStoreException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.marshaller.DefaultBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.mvstore.MVStorePersistenceStore;

public class MVStoreProducerTest {

    @Test
    void store_releases_file_lock_on_dispose() throws Exception {
        Path db = Files.createTempDirectory("flow").resolve("persistent.db");
        WorkflowBufferFactory factory = DefaultBufferFactory.factory();

        MVStoreProducer producer = new MVStoreProducer();
        MVStorePersistenceStore first = new MVStorePersistenceStore(db.toString(), factory);

        producer.close(first); // exercise the @Disposes method

        // Before the fix this throws: "The file is locked: persistent.db"
        MVStorePersistenceStore second = new MVStorePersistenceStore(db.toString(), factory);
        second.close();
    }

    @Test
    void store_releases_file_lock_on_dispose_error() throws Exception {
        Path db = Files.createTempDirectory("flow").resolve("persistent.db");
        WorkflowBufferFactory factory = DefaultBufferFactory.factory();

        MVStorePersistenceStore first = new MVStorePersistenceStore(db.toString(), factory);

        // Before the fix this throws: "The file is locked: persistent.db"
        String message = Assertions.assertThrows(MVStoreException.class, () -> {
            new MVStorePersistenceStore(db.toString(), factory);
        }).getMessage();

        Assertions.assertTrue(message.contains("The file is locked"));
    }
}
