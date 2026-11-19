package io.quarkiverse.flow.persistence.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanCursor;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashFactory;

class PersistenceWorkflowInfoGeneratorTest {

    private static final Duration SCAN_TIMEOUT = Duration.ofSeconds(2);

    private RedisInstanceTransaction createTransaction() {
        HashFactory hashFactory = mock(HashFactory.class);

        return new RedisInstanceTransaction(
                null,
                mock(KeyCommands.class),
                mock(HashCommands.class),
                mock(ValueCommands.class),
                mock(SetCommands.class),
                mock(WorkflowBufferFactory.class),
                hashFactory);
    }

    private Object createGenerator(
            RedisInstanceTransaction transaction,
            KeyScanCursor<String> cursor) throws Exception {

        Class<?> generatorClass = Class.forName(
                "io.quarkiverse.flow.persistence.redis.RedisInstanceTransaction$PersistenceWorkflowInfoGenerator");

        Constructor<?> constructor = generatorClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);

        return constructor.newInstance(transaction, cursor);
    }

    private Object invokeNext(Object generator) throws Exception {
        var method = generator.getClass().getDeclaredMethod("next");
        method.setAccessible(true);
        return method.invoke(generator);
    }

    private void assertCompletesWithinTimeout(ThrowingTestBlock block) {
        assertThatCode(() -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    block.run();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            future.get(SCAN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldContinueWhenFirstScanBatchIsEmpty() {
        assertCompletesWithinTimeout(() -> {
            RedisInstanceTransaction transaction = createTransaction();
            @SuppressWarnings("unchecked")
            KeyScanCursor<String> cursor = mock(KeyScanCursor.class);

            when(cursor.hasNext()).thenReturn(true, true, false);
            when(cursor.next()).thenReturn(Collections.emptySet(), Collections.emptySet());

            Object generator = createGenerator(transaction, cursor);

            assertThat(invokeNext(generator)).isNull();
        });
    }

    @Test
    void shouldContinueAcrossMultipleEmptyScanBatches() {
        assertCompletesWithinTimeout(() -> {
            RedisInstanceTransaction transaction = createTransaction();
            @SuppressWarnings("unchecked")
            KeyScanCursor<String> cursor = mock(KeyScanCursor.class);

            when(cursor.hasNext()).thenReturn(true, true, true, true, false);
            when(cursor.next()).thenReturn(
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet());

            Object generator = createGenerator(transaction, cursor);

            assertThat(invokeNext(generator)).isNull();
        });
    }

    @Test
    void shouldReturnNullWhenCursorHasNoBatches() throws Exception {
        RedisInstanceTransaction transaction = createTransaction();
        @SuppressWarnings("unchecked")
        KeyScanCursor<String> cursor = mock(KeyScanCursor.class);

        when(cursor.hasNext()).thenReturn(false);

        Object generator = createGenerator(transaction, cursor);

        assertThat(invokeNext(generator)).isNull();
    }

    @Test
    void shouldReturnNullAfterEmptyBatchAndExhaustedCursor() throws Exception {
        RedisInstanceTransaction transaction = createTransaction();
        @SuppressWarnings("unchecked")
        KeyScanCursor<String> cursor = mock(KeyScanCursor.class);

        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(Set.of());

        Object generator = createGenerator(transaction, cursor);

        assertThat(invokeNext(generator)).isNull();
    }

    @FunctionalInterface
    private interface ThrowingTestBlock {
        void run() throws Exception;
    }
}