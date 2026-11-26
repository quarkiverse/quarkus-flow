package io.quarkiverse.persistence.jpa.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.awaitility.Awaitility;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.persistence.common.FlowPersistenceRestore;
import io.quarkiverse.flow.persistence.test.durable.DurableResource;
import io.quarkiverse.flow.persistence.test.durable.EmitWorkflow;
import io.quarkiverse.flow.persistence.test.durable.ListenWorkflow;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusExtensionTest;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

/**
 * The periodic recovery scan runs on a plain thread where no request context is active, so the JPA backend's
 * {@code scanAll} must not depend on one. It must also always run in its own transaction, never in the caller's.
 */
@DisabledOnOs(OS.WINDOWS)
public class JPAScanAllWithoutRequestContextIT {

    private static final String SCAN_THREAD = "flow-persistence-restore-scan";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(ListenWorkflow.class, EmitWorkflow.class, DurableResource.class)
                    .addAsResource("durable-application.properties", "application.properties"))
            .overrideConfigKey("quarkus.flow.persistence.scan-interval", "1s")
            .overrideConfigKey("quarkus.log.min-level", "DEBUG")
            .overrideConfigKey("quarkus.log.category.\"" + FlowPersistenceRestore.class.getName() + "\".level", "DEBUG");

    @Inject
    ListenWorkflow listenWorkflow;

    @Inject
    PersistenceInstanceHandlers handlers;

    @Inject
    TransactionManager transactionManager;

    // QuarkusExtensionTest#assertLogRecords only runs after all tests, so collect the records while the app runs
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void collectRestoreLogs() {
        Logger.getLogger(FlowPersistenceRestore.class.getName()).addHandler(handler);
    }

    @AfterEach
    void stopCollectingRestoreLogs() {
        Logger.getLogger(FlowPersistenceRestore.class.getName()).removeHandler(handler);
    }

    @Test
    @DisplayName("periodic_scan_restores_instances_on_a_thread_without_request_context")
    void periodic_scan_restores_instances_on_a_thread_without_request_context() {
        // The instance stops at the listen task and stays persisted, so every periodic scan finds it
        listenWorkflow.startInstance().subscribe().with(ignore -> {
        });

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    // A failing scan is logged as "Periodic workflow instance recovery scan failed"
                    List<LogRecord> failures = records.stream()
                            .filter(r -> r.getLevel().intValue() >= Level.WARNING.intValue())
                            .toList();
                    assertTrue(failures.isEmpty(),
                            () -> "periodic scan failed: " + failures.stream().map(r -> String.valueOf(r.getThrown()))
                                    .toList());
                    assertFalse(restoredOnScanThread().isEmpty(), "no instance restored by the periodic scan yet");
                });
    }

    @Test
    @DisplayName("scan_all_does_not_reuse_the_caller_transaction")
    void scan_all_does_not_reuse_the_caller_transaction() {
        // The instance stops at the listen task and stays persisted, so the scan finds it
        listenWorkflow.startInstance().subscribe().with(ignore -> {
        });

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> QuarkusTransaction.requiringNew().run(() -> {
                    Transaction caller = currentTransaction();
                    List<Transaction> scanTransactions = new ArrayList<>();
                    try (Stream<WorkflowInstance> instances = handlers.reader().scanAll(listenWorkflow.definition())) {
                        instances.forEach(instance -> scanTransactions.add(currentTransaction()));
                    }
                    assertFalse(scanTransactions.isEmpty(), "no persisted instance scanned yet");
                    assertTrue(scanTransactions.stream().noneMatch(caller::equals),
                            "scanAll must run in its own transaction");
                    assertEquals(caller, currentTransaction(), "the caller transaction must be resumed");
                    assertEquals(Status.STATUS_ACTIVE, status(caller), "the caller transaction must stay active");
                }));
    }

    private Transaction currentTransaction() {
        try {
            return transactionManager.getTransaction();
        } catch (SystemException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int status(Transaction transaction) {
        try {
            return transaction.getStatus();
        } catch (SystemException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<LogRecord> restoredOnScanThread() {
        return records.stream()
                .filter(r -> r instanceof ExtLogRecord ext && SCAN_THREAD.equals(ext.getThreadName()))
                .filter(r -> r.getMessage().startsWith("Restoring workflow instance"))
                .toList();
    }
}
