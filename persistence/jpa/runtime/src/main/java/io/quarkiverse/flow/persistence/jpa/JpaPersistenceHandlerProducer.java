package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.cloudevents.CloudEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.events.EventRegistrationBuilder;
import io.serverlessworkflow.impl.persistence.CorrelationOperations;
import io.serverlessworkflow.impl.persistence.OperationAllStrategyCorrelationInfo;
import io.serverlessworkflow.impl.persistence.PersistenceExecutor;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceHandlers;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfo;
import io.serverlessworkflow.impl.scheduler.AllStrategyCorrelationInfoFactory;

public class JpaPersistenceHandlerProducer {

    @ApplicationScoped
    @Produces
    PersistenceInstanceHandlers jpaPersistenceHandlers(JpaInstanceWriter writer, JpaInstanceReader reader) {
        return new PersistenceInstanceHandlers(writer, reader);
    }

    @ApplicationScoped
    @Produces
    AllStrategyCorrelationInfoFactory correlationFactory(PersistenceExecutor executor,
            PersistenceInstanceOperations operations) {
        return new JpaCorrelationStrategyFactory(executor, operations);
    }

    private static class JpaCorrelationStrategyFactory implements AllStrategyCorrelationInfoFactory {

        private final PersistenceExecutor executor;
        private final PersistenceInstanceOperations operations;

        public JpaCorrelationStrategyFactory(PersistenceExecutor executor, PersistenceInstanceOperations operations) {
            this.executor = executor;
            this.operations = operations;
        }

        @Override
        public AllStrategyCorrelationInfo apply(WorkflowDefinition def) {
            return new JpaCorrelationStrategy(def, executor, operations);
        }
    }

    private static class JpaCorrelationStrategy extends OperationAllStrategyCorrelationInfo {

        public JpaCorrelationStrategy(WorkflowDefinition definition, PersistenceExecutor executor,
                PersistenceInstanceOperations operations) {
            super(definition, executor, operations);
        }

        @Override
        protected Collection<Map<EventRegistrationBuilder, CloudEvent>> doTransaction(
                Function<CorrelationOperations, Collection<Map<EventRegistrationBuilder, CloudEvent>>> function) {
            return QuarkusTransaction.requiringNew().call(() -> super.doTransaction(function));
        }
    }
}
