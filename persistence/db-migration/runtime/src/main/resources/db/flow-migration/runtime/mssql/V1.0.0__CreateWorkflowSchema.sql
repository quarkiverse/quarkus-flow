CREATE TABLE cloud_event_entity
(
    id                VARCHAR(255) NOT NULL,
    reg_id            VARCHAR(255) NOT NULL,
    source            VARCHAR(255) NOT NULL,
    type              VARCHAR(255) NOT NULL,
    subject           VARCHAR(255),
    data_content_type VARCHAR(255),
    data_schema       VARCHAR(255),
    [time]            DATETIMEOFFSET(6),
    data              VARBINARY(MAX),
    extensions        VARBINARY(MAX),
    processed_flag    BIT DEFAULT 0,
    version           TINYINT      NOT NULL CHECK (version BETWEEN 0 AND 1),
    PRIMARY KEY (id)
    );

CREATE TABLE workflow_instance_entity
(
    application_id     VARCHAR(255)      NOT NULL,
    instance_id        VARCHAR(255)      NOT NULL,
    workflow_name      VARCHAR(255)      NOT NULL,
    workflow_namespace VARCHAR(255)      NOT NULL,
    workflow_version   VARCHAR(255)      NOT NULL,
    started_at         DATETIMEOFFSET(6) NOT NULL,
    status             TINYINT CHECK (status BETWEEN 0 AND 6),
    input              VARBINARY(MAX),
    PRIMARY KEY (application_id, instance_id)
);

CREATE TABLE task_info_entity
(
    application_id       VARCHAR(255) NOT NULL,
    workflow_instance_id VARCHAR(255) NOT NULL,
    json_pointer         VARCHAR(255) NOT NULL,
    iteration            INT          NOT NULL,
    task_type            INT          NOT NULL CHECK (task_type IN (1, 2)),
    is_end_node          BIT,
    retry_attempt        INT,
    instant              DATETIMEOFFSET(6),
    next_position        VARCHAR(255),
    context              VARBINARY(MAX),
    model                VARBINARY(MAX),
    context_hash_key     VARBINARY(MAX),
    context_hash_index   VARBINARY(MAX),
    model_hash_key       VARBINARY(MAX),
    model_hash_index     VARBINARY(MAX),
    PRIMARY KEY (iteration, application_id, json_pointer, workflow_instance_id),
    CHECK (task_type <> 1 OR (is_end_node IS NOT NULL)),
    CHECK (task_type <> 2 OR (retry_attempt IS NOT NULL)),
    CONSTRAINT fk_task_workflow_instance
        FOREIGN KEY (application_id, workflow_instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
            ON DELETE CASCADE
);

CREATE TABLE hash_mapping_info_entity
(
    id       VARCHAR(255) NOT NULL,
    instance VARCHAR(255),
    [key]    VARBINARY(255),
    data     VARBINARY(MAX),
    PRIMARY KEY (id)
);

CREATE INDEX hashKey_idx ON hash_mapping_info_entity ([key]);
CREATE INDEX instance_idx ON hash_mapping_info_entity (instance);

CREATE TABLE task_metadata_entity
(
    meta_name            VARCHAR(255) NOT NULL,
    iteration            INT          NOT NULL,
    json_pointer         VARCHAR(255) NOT NULL,
    application_id       VARCHAR(255) NOT NULL,
    workflow_instance_id VARCHAR(255) NOT NULL,
    hash_key             VARBINARY(MAX),
    hash_index           VARBINARY(MAX),
    raw_value            VARBINARY(MAX),
    PRIMARY KEY (meta_name, iteration, json_pointer, application_id, workflow_instance_id),
    CONSTRAINT fk_task_metadata_task
        FOREIGN KEY (iteration, application_id, json_pointer, workflow_instance_id)
            REFERENCES task_info_entity (iteration, application_id, json_pointer, workflow_instance_id)
            ON DELETE CASCADE
);

CREATE TABLE workflow_metadata_entity
(
    meta_name            VARCHAR(255) NOT NULL,
    instance_id          VARCHAR(255) NOT NULL,
    application_id       VARCHAR(255) NOT NULL,
    workflow_instance_id VARCHAR(255),
    hash_key             VARBINARY(MAX),
    hash_index           VARBINARY(MAX),
    raw_value            VARBINARY(MAX),
    PRIMARY KEY (meta_name, instance_id, application_id),
    CONSTRAINT fk_workflow_metadata_workflow
        FOREIGN KEY (application_id, instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
            ON DELETE CASCADE
);
