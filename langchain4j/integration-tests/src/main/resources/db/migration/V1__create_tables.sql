CREATE TABLE cloud_event_entity
(
    id                VARCHAR(255) NOT NULL,
    reg_id            VARCHAR(255) NOT NULL,
    source            VARCHAR(255) NOT NULL,
    type              VARCHAR(255) NOT NULL,
    subject           VARCHAR(255),
    data_content_type VARCHAR(255),
    data_schema       VARCHAR(255),
    time              TIMESTAMP(6) WITH TIME ZONE,
    data              VARBINARY,
    extensions        VARBINARY,
    processed_flag    BOOLEAN DEFAULT FALSE,
    version           TINYINT      NOT NULL CHECK (version BETWEEN 0 AND 1),
    PRIMARY KEY (id)
);

CREATE TABLE workflow_instance_entity
(
    application_id     VARCHAR(255)                NOT NULL,
    instance_id        VARCHAR(255)                NOT NULL,
    workflow_name      VARCHAR(255)                NOT NULL,
    workflow_namespace VARCHAR(255)                NOT NULL,
    workflow_version   VARCHAR(255)                NOT NULL,
    started_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status             TINYINT CHECK (status BETWEEN 0 AND 6),
    input              VARBINARY,
    PRIMARY KEY (application_id, instance_id)
);

CREATE TABLE task_info_entity
(
    application_id      VARCHAR(255) NOT NULL,
    workflow_instance_id VARCHAR(255) NOT NULL,
    json_pointer        VARCHAR(255) NOT NULL,
    iteration           INTEGER      NOT NULL,
    task_type           INTEGER      NOT NULL CHECK (task_type IN (1, 2)),
    is_end_node         BOOLEAN,
    retry_attempt       INTEGER,
    instant             TIMESTAMP(6) WITH TIME ZONE,
    next_position       VARCHAR(255),
    context             VARBINARY,
    model               VARBINARY,
    PRIMARY KEY (iteration, application_id, json_pointer, workflow_instance_id),
    CHECK (task_type <> 1 OR (is_end_node IS NOT NULL)),
    CHECK (task_type <> 2 OR (retry_attempt IS NOT NULL)),
    CONSTRAINT fk_task_workflow_instance
        FOREIGN KEY (application_id, workflow_instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
);
