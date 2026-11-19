package io.quarkiverse.flow.persistence.redis.test;

import static io.serverlessworkflow.api.WorkflowReader.readWorkflowFromClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;

/**
 * Paging of {@code RedisInstanceTransaction.scanAll}, the listing the crash recovery restores its
 * instances from.
 *
 * <p>
 * The listing is a Redis {@code SCAN} filtered server side by {@code MATCH}. Redis answers such a
 * scan page by page and a page may contain <em>no</em> matching key at all, while later pages still
 * do. A listing that stops at the first page without a match therefore loses instances silently -
 * the recovery reports the definitions it found, restores nothing, and the affected workflows stay
 * parked forever.
 * </p>
 *
 * <p>
 * Page contents cannot be dictated, but they can be forced: every key that does not match the
 * instance prefix fills a page without contributing an instance. The tests below interleave those
 * foreign keys with the persisted instances, so the scan has to walk many pages - empty and non-empty
 * ones - before it is done. What is asserted is the outcome the recovery depends on: every persisted
 * instance is returned, unreadable ones are skipped instead of ending the stream, and the stream
 * terminates.
 * </p>
 */
@QuarkusTest
@DisplayName("RedisInstanceScanPaging")
public class RedisInstanceScanPagingIT {

    /** Foreign keys: they match nothing in the instance scan and only exist to fill scan pages. */
    private static final String NOISE_PREFIX = "it-scan-paging:noise:";

    /** Generous bound: a scan that never terminates must fail the test instead of hanging it. */
    private static final Duration SCAN_TIMEOUT = Duration.ofSeconds(60);

    @Inject
    PersistenceInstanceHandlers handlers;

    @Inject
    RedisDataSource ds;

    private static WorkflowApplication app;
    private static WorkflowDefinition definition;

    private KeyCommands<String> keys;
    private ValueCommands<String, String> values;
    private final Set<String> instanceIds = new LinkedHashSet<>();

    @BeforeAll
    static void initApp() throws IOException {
        app = WorkflowApplication.builder().build();
        definition = app.workflowDefinition(readWorkflowFromClasspath("simple-expression.yaml"));
    }

    @AfterAll
    static void closeApp() {
        if (app != null) {
            app.close();
        }
    }

    @BeforeEach
    void setUp() {
        keys = ds.key(String.class);
        values = ds.value(String.class);
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("instances spread over several scan pages are all returned")
    void scanAll_returns_instances_of_every_page() {
        // Three instances, each separated by enough foreign keys to land in its own page.
        String first = persistInstance();
        addNoise(40);
        String second = persistInstance();
        addNoise(40);
        String third = persistInstance();
        addNoise(40);

        List<String> found = scanInstanceIds();

        assertThat(found).containsExactlyInAnyOrder(first, second, third);
        assertThat(noiseKeyCount()).isGreaterThan(100);
    }

    @Test
    @DisplayName("a scan page without any match does not end the listing")
    void scanAll_continues_after_an_empty_page() {
        // The regression case: matches separated by pages that hold nothing but foreign keys.
        String first = persistInstance();
        addNoise(60);
        String second = persistInstance();

        List<String> found = assertTimeoutPreemptively(SCAN_TIMEOUT, this::scanInstanceIds);

        assertThat(found).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("several consecutive empty pages are walked until the cursor is done")
    void scanAll_walks_many_consecutive_empty_pages() {
        String first = persistInstance();
        addNoise(300);
        String second = persistInstance();
        addNoise(300);

        List<String> found = assertTimeoutPreemptively(SCAN_TIMEOUT, this::scanInstanceIds);

        assertThat(found).containsExactlyInAnyOrder(first, second);
        assertThat(noiseKeyCount()).isGreaterThan(500);
    }

    @Test
    @DisplayName("an instance deleted while listing is skipped, the listing continues")
    void scanAll_skips_instances_deleted_during_the_scan() {
        String first = persistInstance();
        String second = persistInstance();
        String third = persistInstance();
        addNoise(40);

        // The listing is lazy: a key is read when the iterator reaches it, not when its page is
        // fetched. Removing the remaining records after the first element therefore hits their read
        // - the situation of a key deleted between SCAN and HGETALL. Which instance comes first is
        // up to the scan order, so only the first one is known to survive.
        Stream<WorkflowInstance> stream = handlers.reader().scanAll(definition);
        List<String> found = new ArrayList<>();
        String firstRead;
        try (stream) {
            var iterator = stream.iterator();
            assertThat(iterator.hasNext()).isTrue();
            firstRead = iterator.next().id();
            found.add(firstRead);
            for (String id : List.of(first, second, third)) {
                if (!id.equals(firstRead)) {
                    deleteKeysOf(id);
                }
            }
            iterator.forEachRemaining(instance -> found.add(instance.id()));
        }

        assertThat(found).containsExactly(firstRead);
        assertThat(instanceExists(firstRead)).isTrue();

        // The survivor is still listed by a fresh scan, so skipping did not damage the listing.
        assertThat(scanInstanceIds()).containsExactly(firstRead);
    }

    @Test
    @DisplayName("no persisted instance yields an empty listing without error")
    void scanAll_returns_nothing_when_no_instance_is_persisted() {
        addNoise(200);

        List<String> found = assertTimeoutPreemptively(SCAN_TIMEOUT, this::scanInstanceIds);

        assertThat(found).isEmpty();
        assertThat(noiseKeyCount()).isGreaterThan(100);
    }

    // ---------------------------------------------------------------- helpers

    /** Persists a started instance through the regular writer and returns its id. */
    private String persistInstance() {
        String id = "it-scan-" + UUID.randomUUID();
        WorkflowModel model = app.modelFactory().from(Map.of("name", "scan-paging"));
        handlers.writer().started(new TestWorkflowContext(new TestWorkflowInstance(id, model), model, definition))
                .join();
        instanceIds.add(id);
        assertThat(instanceExists(id))
                .as("instance %s must be persisted before the scan can find it", id)
                .isTrue();
        return id;
    }

    private void addNoise(int count) {
        for (int i = 0; i < count; i++) {
            values.set(NOISE_PREFIX + UUID.randomUUID(), "x");
        }
    }

    private List<String> scanInstanceIds() {
        try (Stream<WorkflowInstance> stream = handlers.reader().scanAll(definition)) {
            return stream.map(WorkflowInstance::id).toList();
        }
    }

    private long noiseKeyCount() {
        return countKeys(NOISE_PREFIX + "*");
    }

    private long countKeys(String pattern) {
        long count = 0;
        var cursor = keys.scan(new KeyScanArgs().match(pattern).count(500));
        while (cursor.hasNext()) {
            count += cursor.next().size();
        }
        return count;
    }

    private boolean instanceExists(String instanceId) {
        return countKeys("*" + instanceId) > 0;
    }

    private void deleteKeysOf(String... instanceIdsToDelete) {
        for (String id : instanceIdsToDelete) {
            var cursor = keys.scan(new KeyScanArgs().match("*" + id).count(500));
            while (cursor.hasNext()) {
                cursor.next().forEach(keys::del);
            }
            // The discovery index is keyed by the base64 encoded instance id, not by its suffix.
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(id.getBytes(StandardCharsets.UTF_8));
            var indexCursor = keys.scan(new KeyScanArgs().match(encoded + ":*").count(500));
            while (indexCursor.hasNext()) {
                indexCursor.next().forEach(keys::del);
            }
            instanceIds.remove(id);
        }
    }

    /** Removes everything this test wrote: instance records, their index and blob keys, noise. */
    private void cleanUp() {
        if (keys == null) {
            return;
        }
        for (String id : new ArrayList<>(instanceIds)) {
            deleteKeysOf(id);
        }
        instanceIds.clear();
        var noise = keys.scan(new KeyScanArgs().match(NOISE_PREFIX + "*").count(500));
        while (noise.hasNext()) {
            noise.next().forEach(keys::del);
        }
    }

    /** Instance data as the writer reads it when an instance is persisted as started. */
    private record TestWorkflowInstance(String id, WorkflowModel input) implements WorkflowInstanceData {

        @Override
        public Instant startedAt() {
            return Instant.now();
        }

        @Override
        public Instant completedAt() {
            return null;
        }

        @Override
        public WorkflowStatus status() {
            return WorkflowStatus.PENDING;
        }

        @Override
        public WorkflowModel context() {
            return input;
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }

    /** Context data pointing at the definition the scan is asked for. */
    private record TestWorkflowContext(WorkflowInstanceData instanceData, WorkflowModel context,
            WorkflowDefinition definition) implements WorkflowContextData {
    }
}
