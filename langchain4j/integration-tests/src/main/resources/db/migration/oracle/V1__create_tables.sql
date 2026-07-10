CREATE TABLE cloud_event_entity
(
    id                VARCHAR2(255) NOT NULL,
    reg_id            VARCHAR2(255) NOT NULL,
    source            VARCHAR2(255) NOT NULL,
    type              VARCHAR2(255) NOT NULL,
    subject           VARCHAR2(255),
    data_content_type VARCHAR2(255),
    data_schema       VARCHAR2(255),
    time              TIMESTAMP(6) WITH TIME ZONE,
    data              BLOB,
    extensions        BLOB,
    processed_flag    NUMBER(1, 0) DEFAULT 0,
    version           NUMBER(3, 0)  NOT NULL CHECK (version BETWEEN 0 AND 1),
    PRIMARY KEY (id)
);

CREATE TABLE workflow_instance_entity
(
    application_id     VARCHAR2(255)               NOT NULL,
    instance_id        VARCHAR2(255)               NOT NULL,
    workflow_name      VARCHAR2(255)               NOT NULL,
    workflow_namespace VARCHAR2(255)               NOT NULL,
    workflow_version   VARCHAR2(255)               NOT NULL,
    started_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status             NUMBER(3, 0) CHECK (status BETWEEN 0 AND 6),
    input              BLOB,
    PRIMARY KEY (application_id, instance_id)
);

CREATE TABLE task_info_entity
(
    application_id      VARCHAR2(255) NOT NULL,
    workflow_instance_id VARCHAR2(255) NOT NULL,
    json_pointer        VARCHAR2(255) NOT NULL,
    iteration           NUMBER(10, 0) NOT NULL,
    task_type           NUMBER(10, 0) NOT NULL CHECK (task_type IN (1, 2)),
    is_end_node         NUMBER(1, 0),
    retry_attempt       NUMBER(10, 0),
    instant             TIMESTAMP(6) WITH TIME ZONE,
    next_position       VARCHAR2(255),
    context             BLOB,
    model               BLOB,
    PRIMARY KEY (iteration, application_id, json_pointer, workflow_instance_id),
    CHECK (task_type <> 1 OR (is_end_node IS NOT NULL)),
    CHECK (task_type <> 2 OR (retry_attempt IS NOT NULL)),
    CONSTRAINT fk_task_workflow_instance
        FOREIGN KEY (application_id, workflow_instance_id)
            REFERENCES workflow_instance_entity (application_id, instance_id)
);