package io.quarkiverse.flow.langchain4j.workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.langchain4j.schedule.ScheduleType;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.fluent.func.dsl.FuncDSL;

/**
 * A template method class to serve as skeleton for generating schedulable workflows with Gizmo.
 */
@ApplicationScoped
public abstract class AbstractSchedulableFlow extends Flow {

    static final Logger log = LoggerFactory.getLogger(AbstractSchedulableFlow.class);

    @Inject
    protected ObjectMapper jackson;

    public ObjectMapper objectMapper() {
        return jackson;
    }

    // the task's name is the same name as the planner agent method
    protected abstract String taskName();

    protected abstract <T> T consume(CloudEvent ce);

    protected abstract String namespace();

    protected abstract String name();

    protected abstract String version();

    protected abstract ScheduleType scheduleType();

    protected abstract String value();

    @Override
    public Workflow descriptor() {
        FuncWorkflowBuilder builder = FuncWorkflowBuilder
                .workflow(name(), namespace(), version())
                .tasks(FuncDSL.function(taskName(), cloudEvent -> {
                    if (cloudEvent != null) {
                        log.trace("Handling CloudEvent#data: {}", cloudEvent.getData());
                    }
                    return consume(cloudEvent);
                }, CloudEvent.class));
        switch (scheduleType()) {
            case CRON -> builder.schedule(scheduleBuilder -> scheduleBuilder.cron(value()));
            case EVENT -> builder.schedule(scheduleBuilder -> {
                scheduleBuilder.on(consumptionStrategy -> consumptionStrategy
                        .one(eventFilter -> eventFilter.with(eventProps -> eventProps.type(value()))));
            });
            case EVERY -> builder.schedule(scheduleBuilder -> scheduleBuilder.every(value()));
        }

        return builder.build();
    }

}
