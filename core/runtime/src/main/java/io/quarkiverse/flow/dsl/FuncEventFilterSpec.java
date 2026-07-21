package io.quarkiverse.flow.dsl;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.core.CloudEventUtils;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.jackson.PojoCloudEventDataMapper;
import io.quarkiverse.flow.dsl.types.ContextPredicate;
import io.quarkiverse.flow.dsl.types.FilterPredicate;
import io.quarkiverse.flow.dsl.types.SerializablePredicate;
import io.serverlessworkflow.fluent.spec.dsl.AbstractEventFilterSpec;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.jackson.JsonUtils;

/**
 * Fluent DSL specification builder for configuring CloudEvent filters within a Serverless Workflow
 * execution.
 */
public final class FuncEventFilterSpec
        extends AbstractEventFilterSpec<FuncEventFilterSpec, FuncEventFilterPropertiesBuilder, FuncEventFilterBuilder> {

    /**
     * Returns the current instance of the builder.
     *
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    @Override
    protected FuncEventFilterSpec self() {
        return this;
    }

    /**
     * Configures the filter to match incoming event based on a Predicate. This is the Listen
     * counterpart to Emit's jsonData(Function).
     *
     * @param predicate the predicate to evaluate against the entire {@link CloudEvent}.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec envelope(SerializablePredicate<CloudEvent> predicate) {
        addPropertyStep(e -> e.envelope(predicate));
        return this;
    }

    /**
     * Configures the filter to match incoming event data based on a ContextPredicate, allowing
     * evaluation against the {@link CloudEvent} and the current {@link WorkflowContextData}.
     *
     * @param predicate the context-aware predicate to evaluate.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec envelope(ContextPredicate<CloudEvent> predicate) {
        addPropertyStep(e -> e.envelope(predicate));
        return this;
    }

    /**
     * Configures the filter to match incoming event data based on a FilterPredicate, allowing
     * evaluation against the {@link CloudEvent}, {@link WorkflowContextData}, and {@link
     * TaskContextData}.
     *
     * @param predicate the filter predicate to evaluate.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec envelope(FilterPredicate<CloudEvent> predicate) {
        addPropertyStep(e -> e.envelope(predicate));
        return this;
    }

    /**
     * Configures the filter to match incoming event data based on a Predicate evaluated directly
     * against the raw {@link CloudEventData} payload.
     *
     * @param predicate the predicate to evaluate against the event data.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec data(SerializablePredicate<CloudEventData> predicate) {
        addPropertyStep(e -> e.data(predicate));
        return this;
    }

    /**
     * Configures the filter to match incoming event data based on a ContextPredicate evaluated
     * directly against the raw {@link CloudEventData} payload and the current {@link
     * WorkflowContextData}.
     *
     * @param predicate the context-aware predicate to evaluate.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec data(ContextPredicate<CloudEventData> predicate) {
        addPropertyStep(e -> e.data(predicate));
        return this;
    }

    /**
     * Configures the filter to match incoming event data based on a FilterPredicate evaluated
     * directly against the raw {@link CloudEventData} payload, {@link WorkflowContextData}, and
     * {@link TaskContextData}.
     *
     * @param predicate the filter predicate to evaluate.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec data(FilterPredicate<CloudEventData> predicate) {
        addPropertyStep(e -> e.data(predicate));
        return this;
    }

    /**
     * Evaluates the given predicate against the CloudEvent data payload, automatically parsed as a
     * Map.
     *
     * <p>
     * For example, you can evaluate this filter as:
     *
     * <pre>
     * .dataAsMap(map -> "123".equals(map.get("orderId")))
     * </pre>
     *
     * @param predicate the predicate to evaluate against the parsed Map.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec dataAsMap(SerializablePredicate<Map<String, Object>> predicate) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce) -> {
                            Map<String, Object> ceDataMap = asCEDataMap(ce);
                            return !ceDataMap.isEmpty() && predicate.test(ceDataMap);
                        }));
        return this;
    }

    /**
     * Evaluates the given ContextPredicate against the CloudEvent data payload (parsed as a Map) and
     * the current WorkflowContextData.
     *
     * @param predicate the context-aware predicate to evaluate against the parsed Map.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec dataAsMap(ContextPredicate<Map<String, Object>> predicate) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context) -> {
                            Map<String, Object> ceDataMap = asCEDataMap(ce);
                            return !ceDataMap.isEmpty() && predicate.test(ceDataMap, context);
                        }));
        return this;
    }

    /**
     * Evaluates the given FilterPredicate against the CloudEvent data payload (parsed as a Map), the
     * current WorkflowContextData, and the TaskContextData.
     *
     * @param predicate the filter predicate to evaluate against the parsed Map and task/workflow
     *        contexts.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec dataAsMap(FilterPredicate<Map<String, Object>> predicate) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context, TaskContextData taskContext) -> {
                            Map<String, Object> ceDataMap = asCEDataMap(ce);
                            return !ceDataMap.isEmpty() && predicate.test(ceDataMap, context, taskContext);
                        }));
        return this;
    }

    /**
     * Evaluates the given predicate against the CloudEvent data payload, automatically parsed into
     * the specified target type.
     *
     * <p>
     * For example, you can evaluate this filter as:
     *
     * <pre>
     * .dataAs(Order.class, order -> order.getId() == 123)
     * </pre>
     *
     * @param targetType The class of the type <T> to deserialize the payload into.
     * @param predicate The predicate to evaluate against the parsed object.
     * @param <T> The target type.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public <T> FuncEventFilterSpec dataAs(Class<T> targetType, SerializablePredicate<T> predicate) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce) -> {
                            T parsedData = parseCEData(ce, targetType);
                            return parsedData != null && predicate.test(parsedData);
                        }));
        return this;
    }

    /**
     * Evaluates the given ContextPredicate against the CloudEvent data payload (parsed into the
     * specified target type) and the current WorkflowContextData.
     *
     * @param targetType The class of the type <T> to deserialize the payload into.
     * @param predicate The context-aware predicate to evaluate against the parsed object.
     * @param <T> The target type.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public <T> FuncEventFilterSpec dataAs(Class<T> targetType, ContextPredicate<T> predicate) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context) -> {
                            T parsedData = parseCEData(ce, targetType);
                            return parsedData != null && predicate.test(parsedData, context);
                        }));
        return this;
    }

    /**
     * Evaluates the given FilterPredicate against the CloudEvent data payload (parsed into the
     * specified target type), the current WorkflowContextData, and the TaskContextData.
     *
     * @param targetType The class of the type <T> to deserialize the payload into.
     * @param predicate The filter predicate to evaluate against the parsed object and contexts.
     * @param <T> The target type.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public <T> FuncEventFilterSpec dataAs(Class<T> targetType, FilterPredicate<T> predicate) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context, TaskContextData taskContext) -> {
                            T parsedData = parseCEData(ce, targetType);
                            return parsedData != null && predicate.test(parsedData, context, taskContext);
                        }));
        return this;
    }

    /**
     * Filter events which field carries the current workflow instance ID. For example, given the data
     * payload:
     *
     * <pre>
     * {
     * "order": { "number": 123 },
     * "workflowInstanceId": "123456789"
     * }
     * </pre>
     *
     * <p>
     * You would call <code>dataByInstanceId("workflowInstanceId")</code>. Events matching the
     * current instance ID in the field <code>workflowInstanceId</code> would match this filter.
     *
     * @param fieldName name of the field in the CE data that carries the instance ID.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec dataByInstanceId(String fieldName) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context) -> {
                            Map<String, Object> ceDataMap = asCEDataMap(ce);
                            return Objects.equals(ceDataMap.get(fieldName), context.instanceData().id());
                        }));
        return this;
    }

    /**
     * Same as {@link #dataByInstanceId(String)}, but now the filter looks at the CE extension name.
     *
     * @param extensionName the extension name where to fetch the given workflow instance ID to match
     *        with the current execution.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec extensionByInstanceId(String extensionName) {
        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context) -> context.instanceData().id()
                                .equals(ce.getExtension(extensionName))));
        return this;
    }

    /**
     * Matches events that carry in the CE data payload fields with the same values as the input of
     * the current task.
     *
     * <p>
     * For example, you can filter events carrying specific data using <code>
     * dataFields("orderId", "customerId")</code>. Events where the CE data payload matches the task
     * input for all provided fields will pass this filter.
     *
     * @param fieldNames the field names to match this filter.
     * @return the current {@link FuncEventFilterSpec} instance.
     */
    public FuncEventFilterSpec dataFields(String... fieldNames) {
        if (fieldNames == null || fieldNames.length == 0)
            return this;

        addPropertyStep(
                e -> e.envelope(
                        (CloudEvent ce, WorkflowContextData context, TaskContextData taskContext) -> {
                            Map<String, Object> input = taskContext.rawInput().asMap().orElse(Map.of());
                            Map<String, Object> ceDataMap = asCEDataMap(ce);

                            return !ceDataMap.isEmpty()
                                    && java.util.Arrays.stream(fieldNames)
                                            .allMatch(
                                                    fieldName -> Objects.equals(ceDataMap.get(fieldName),
                                                            input.get(fieldName)));
                        }));
        return this;
    }

    /**
     * Helper method to safely extract and parse the {@link CloudEvent} data payload into a Map.
     *
     * @param ce the incoming {@link CloudEvent} to parse.
     * @return a {@link Map} containing the parsed data, or an empty map if parsing fails or data is
     *         null.
     */
    private Map<String, Object> asCEDataMap(CloudEvent ce) {
        if (ce.getData() == null)
            return Map.of();
        PojoCloudEventData<Map<String, Object>> mappedData = CloudEventUtils.mapData(
                ce, PojoCloudEventDataMapper.from(JsonUtils.mapper(), new TypeReference<>() {
                }));
        if (mappedData == null || mappedData.getValue() == null) {
            return Map.of();
        }

        return mappedData.getValue();
    }

    /**
     * Helper method to safely parse the {@link CloudEvent} data payload into a specified target class
     * type.
     *
     * @param ce the incoming {@link CloudEvent} to parse.
     * @param targetType the class representing the target type.
     * @param <T> the target type parameter.
     * @return an instance of the parsed type, or null if parsing fails or data is null.
     */
    private <T> T parseCEData(CloudEvent ce, Class<T> targetType) {
        if (ce.getData() == null)
            return null;
        try {
            return JsonUtils.mapper().readValue(ce.getData().toBytes(), targetType);
        } catch (Exception e) {
            return null;
        }
    }
}
