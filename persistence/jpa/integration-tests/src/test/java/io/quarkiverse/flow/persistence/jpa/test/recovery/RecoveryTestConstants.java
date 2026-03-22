package io.quarkiverse.flow.persistence.jpa.test.recovery;

final class RecoveryTestConstants {
    static final String WORKFLOW_NAME = "persistence-recovery";
    static final String RESUME_EVENT_TYPE = "flow.recovery.resume";
    static final String RESUME_EVENT_SOURCE = "urn:flow-recovery-test";

    private RecoveryTestConstants() {
    }
}
