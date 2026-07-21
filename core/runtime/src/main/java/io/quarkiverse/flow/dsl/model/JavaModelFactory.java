package io.quarkiverse.flow.dsl.model;

import java.time.OffsetDateTime;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowModelCollection;
import io.serverlessworkflow.impl.WorkflowModelFactory;

public class JavaModelFactory implements WorkflowModelFactory {
    private final JavaModel TrueModel = new JavaModel(Boolean.TRUE);
    private final JavaModel FalseModel = new JavaModel(Boolean.FALSE);
    private final JavaModel NullModel = new JavaModel(null);

    @Override
    public WorkflowModel combine(Map<String, WorkflowModel> workflowVariables) {
        return new JavaModel(workflowVariables);
    }

    @Override
    public WorkflowModelCollection createCollection() {
        return new JavaModelCollection();
    }

    @Override
    public WorkflowModel from(boolean value) {
        return value ? TrueModel : FalseModel;
    }

    @Override
    public WorkflowModel from(Number value) {
        return new JavaModel(value);
    }

    @Override
    public WorkflowModel from(String value) {
        return new JavaModel(value);
    }

    @Override
    public WorkflowModel from(CloudEvent ce) {
        return new JavaModel(ce);
    }

    @Override
    public WorkflowModel from(CloudEventData ce) {
        return new JavaModel(ce);
    }

    @Override
    public WorkflowModel from(OffsetDateTime value) {
        return new JavaModel(value);
    }

    @Override
    public WorkflowModel from(Map<String, Object> map) {
        return new JavaModel(map);
    }

    @Override
    public WorkflowModel fromNull() {
        return NullModel;
    }

    @Override
    public WorkflowModel fromOther(Object obj) {
        return new JavaModel(obj);
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY + 10;
    }
}
