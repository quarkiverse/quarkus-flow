package io.quarkiverse.flow.dsl;

import java.util.function.Predicate;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.quarkiverse.flow.dsl.types.ContextPredicate;
import io.quarkiverse.flow.dsl.types.EventDataPredicate;
import io.quarkiverse.flow.dsl.types.EventPropertiesPredicate;
import io.quarkiverse.flow.dsl.types.FilterPredicate;
import io.serverlessworkflow.fluent.spec.AbstractEventPropertiesBuilder;

public class FuncEventFilterPropertiesBuilder
        extends AbstractEventPropertiesBuilder<FuncEventFilterPropertiesBuilder> {

    public FuncEventFilterPropertiesBuilder() {
        super(new EventPropertiesPredicate());
    }

    @Override
    protected FuncEventFilterPropertiesBuilder self() {
        return this;
    }

    public FuncEventFilterPropertiesBuilder data(Predicate<CloudEventData> predicate) {
        this.eventProperties.setData(
                new EventDataPredicate().withPredicate(predicate, CloudEventData.class));
        return this;
    }

    public FuncEventFilterPropertiesBuilder data(ContextPredicate<CloudEventData> predicate) {
        this.eventProperties.setData(
                new EventDataPredicate().withPredicate(predicate, CloudEventData.class));
        return this;
    }

    public FuncEventFilterPropertiesBuilder data(FilterPredicate<CloudEventData> predicate) {
        this.eventProperties.setData(
                new EventDataPredicate().withPredicate(predicate, CloudEventData.class));
        return this;
    }

    public FuncEventFilterPropertiesBuilder envelope(Predicate<CloudEvent> predicate) {
        ((EventPropertiesPredicate) eventProperties).withPredicate(predicate, CloudEvent.class);
        return this;
    }

    public FuncEventFilterPropertiesBuilder envelope(ContextPredicate<CloudEvent> predicate) {
        ((EventPropertiesPredicate) eventProperties).withPredicate(predicate, CloudEvent.class);
        return this;
    }

    public FuncEventFilterPropertiesBuilder envelope(FilterPredicate<CloudEvent> predicate) {
        ((EventPropertiesPredicate) eventProperties).withPredicate(predicate, CloudEvent.class);
        return this;
    }
}
