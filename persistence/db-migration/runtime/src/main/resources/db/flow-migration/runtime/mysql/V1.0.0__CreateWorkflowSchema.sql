CREATE TABLE cloud_event_entity
(
    id                VARCHAR(255) NOT NULL,
    reg_id            VARCHAR(255) NOT NULL,
    source            VARCHAR(255) NOT NULL,
    type              VARCHAR(255) NOT NULL,
    subject           VARCHAR(255),
    data_content_type VARCHAR(255),
    data_schema       VARCHAR(255),
    `time`            DATETIME(6),
    data              LONGBLOB,
    extensions        LONGBLOB,
    processed_flag    BOOLEAN DEFAULT FALSE,
    version           TINYINT      NOT NULL CHECK (version BETWEEN 0 AND 1),
    PRIMARY KEY (id)
);

CREATE TABLE workflow_instance_entity
(
    application_id     VARCHAR(255) CHARACTER SET ascii NOT NULL,
    instance_id        VARCHAR(255) CHARACTER SET ascii NOT NULL,
    workflow_name      VARCHAR(255) NOT NULL,
    workflow_namespace VARCHAR(255) NOT NULL,
    workflow_version   VARCHAR(255) NOT NULL,
    started_at         DATETIME(6)  NOT NULL,
    status             TINYINT CHECK (status BETWEEN 0 AND 6),
    input              LONGBLOB,
    PRIMARY KEY (application_id, instance_id)
);

CREATE TABLE task_info_entity
(
    application_id       VARCHAR(255) CHARACTER SET ascii NOT NULL,
    workflow_instance_id VARCHAR(255) CHARACTER SET ascii NOT NULL,
    json_pointer         VARCHAR(255) CHARACTER SET ascii NOT NULL,
    iteration            INTEGER      NOT NULL,
    task_type            INTEGER      NOT NULL CHECK (task_type IN (1, 2)),
    is_end_node          BOOLEAN,
    retry_attempt        INTEGER,
    instant              DATETIME(6),
    next_position        VARCHAR(255),
    context              LONGBLOB,
    model                LONGBLOB,
    context_hash_key     LONGBLOB,
    context_hash_index   LONGBLOB,
    model_hash_key       LONGBLOB,
    model_hash_index     LONGBLOB,
    PRIMARY KEY (iteration, application_id, json_pointer, workflow_instance_id),
    CHECK (task_type <> 1 OR (is_end_node IS NOT NULL)),
    CHECK (task_type <> 2 OR (retry_attempt IS NOT NULL)),
    CONSTRAINT fk_task_workflow_instance
        FOREIGN KEY (application_id, workflow_instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
);

CREATE TABLE hash_mapping_info_entity
(
    id       VARCHAR(255) NOT NULL,
    instance VARCHAR(255),
    `key`    VARBINARY(255),
    data     LONGBLOB,
    PRIMARY KEY (id)
);

CREATE INDEX hashKey_idx ON hash_mapping_info_entity (`key`);
CREATE INDEX instance_idx ON hash_mapping_info_entity (instance);

CREATE TABLE task_metadata_entity
(
    meta_name            VARCHAR(255) CHARACTER SET ascii NOT NULL,
    iteration            INTEGER      NOT NULL,
    json_pointer         VARCHAR(255) CHARACTER SET ascii NOT NULL,
    application_id       VARCHAR(255) CHARACTER SET ascii NOT NULL,
    workflow_instance_id VARCHAR(255) CHARACTER SET ascii NOT NULL,
    hash_key             LONGBLOB,
    hash_index           LONGBLOB,
    raw_value            LONGBLOB,
    PRIMARY KEY (meta_name, iteration, json_pointer, application_id, workflow_instance_id),
    CONSTRAINT fk_task_metadata_task
        FOREIGN KEY (iteration, application_id, json_pointer, workflow_instance_id)
            REFERENCES task_info_entity (iteration, application_id, json_pointer, workflow_instance_id)
);

CREATE TABLE workflow_metadata_entity
(
    meta_name            VARCHAR(255) CHARACTER SET ascii NOT NULL,
    instance_id          VARCHAR(255) CHARACTER SET ascii NOT NULL,
    application_id       VARCHAR(255) CHARACTER SET ascii NOT NULL,
    workflow_instance_id VARCHAR(255) CHARACTER SET ascii,
    hash_key             LONGBLOB,
    hash_index           LONGBLOB,
    raw_value            LONGBLOB,
    PRIMARY KEY (meta_name, instance_id, application_id),
    CONSTRAINT fk_workflow_metadata_workflow
        FOREIGN KEY (application_id, instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
);
