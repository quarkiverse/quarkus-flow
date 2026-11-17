package io.quarkiverse.flow.persistence.jpa.test;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.Instant;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceEntity;
import io.quarkiverse.flow.persistence.jpa.WorkflowInstanceKey;
import io.quarkiverse.flow.persistence.spi.InstanceLocator;
import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowStatus;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
public class InstanceLocatorIT {

    @Inject
    InstanceLocator locator;

    @Inject
    EntityManager em;

    private static final String NAMESPACE = "test-ns";
    private static final String NAME = "test-workflow";
    private static final String VERSION = "1.0.0";
    private static final String INSTANCE_ID = "instance-123";
    private static final String APP_ID_1 = "app-1";
    private static final String APP_ID_2 = "app-2";

    @BeforeEach
    @Transactional
    void setup() {
        // Clear any existing data
        em.createQuery("DELETE FROM WorkflowInstanceEntity").executeUpdate();
    }

    private WorkflowInstanceEntity create_instance(String appId, String namespace, String name, String version,
            String instanceId, Instant startedAt, WorkflowStatus status) throws Exception {
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
        setField(entity, "key", new WorkflowInstanceKey(instanceId, appId));
        setField(entity, "workflowNamespace", namespace);
        setField(entity, "workflowName", name);
        setField(entity, "workflowVersion", version);
        setField(entity, "startedAt", startedAt);
        if (status != null) {
            entity.setStatus(status);
        }
        return entity;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = WorkflowInstanceEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("locate returns instance when row exists")
    @Transactional
    void locate_returns_instance_when_exists() throws Exception {
        // Create and persist an instance
        WorkflowInstanceEntity entity = create_instance(APP_ID_1, NAMESPACE, NAME, VERSION, INSTANCE_ID, Instant.now(), null);
        em.persist(entity);
        em.flush();

        // Locate the instance
        var result = locator.locate(NAMESPACE, NAME, VERSION, INSTANCE_ID);

        assertThat(result)
                .isPresent()
                .hasValueSatisfying(location -> {
                    assertThat(location.namespace()).isEqualTo(NAMESPACE);
                    assertThat(location.name()).isEqualTo(NAME);
                    assertThat(location.version()).isEqualTo(VERSION);
                    assertThat(location.instanceId()).isEqualTo(INSTANCE_ID);
                    assertThat(location.applicationId()).isEqualTo(APP_ID_1);
                });
    }

    @Test
    @DisplayName("locate returns cross_shard instance with different applicationId")
    @Transactional
    void locate_returns_cross_shard_instance() throws Exception {
        // Create and persist instances with same workflow but different app ids
        WorkflowInstanceEntity entity1 = create_instance(APP_ID_1, NAMESPACE, NAME, VERSION, "other-instance", Instant.now(),
                null);
        em.persist(entity1);

        WorkflowInstanceEntity entity2 = create_instance(APP_ID_2, NAMESPACE, NAME, VERSION, INSTANCE_ID, Instant.now(), null);
        em.persist(entity2);
        em.flush();

        // Locate the instance scoped by workflow id, not app id
        var result = locator.locate(NAMESPACE, NAME, VERSION, INSTANCE_ID);

        assertThat(result)
                .isPresent()
                .hasValueSatisfying(location -> {
                    assertThat(location.applicationId()).isEqualTo(APP_ID_2);
                });
    }

    @Test
    @DisplayName("locate returns empty when instance not found")
    @Transactional
    void locate_returns_empty_when_not_found() {
        // Locate non-existent instance
        var result = locator.locate(NAMESPACE, NAME, VERSION, "non-existent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("locate returns empty when workflow id differs")
    @Transactional
    void locate_isolation_by_workflow_id() throws Exception {
        // Create instance with same instanceId but different workflow id
        WorkflowInstanceEntity entity = create_instance(APP_ID_1, "different-ns", "different-name", "2.0.0", INSTANCE_ID,
                Instant.now(), null);
        em.persist(entity);
        em.flush();

        // Locate with the original workflow id should not find it
        var result = locator.locate(NAMESPACE, NAME, VERSION, INSTANCE_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("locate includes the owning application id")
    @Transactional
    void locate_includes_metadata() throws Exception {
        Instant startedAt = Instant.now();
        WorkflowInstanceEntity entity = create_instance(APP_ID_1, NAMESPACE, NAME, VERSION, INSTANCE_ID, startedAt,
                WorkflowStatus.RUNNING);
        em.persist(entity);
        em.flush();

        var result = locator.locate(NAMESPACE, NAME, VERSION, INSTANCE_ID);

        assertThat(result)
                .isPresent()
                .hasValueSatisfying(location -> {
                    assertThat(location.applicationId()).isEqualTo(APP_ID_1);
                });
    }
}
