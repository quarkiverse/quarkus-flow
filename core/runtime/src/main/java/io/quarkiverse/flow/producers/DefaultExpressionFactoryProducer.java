package io.quarkiverse.flow.producers;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.expressions.ExpressionFactory;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;

public class DefaultExpressionFactoryProducer {
    @Produces
    @Singleton
    @DefaultBean
    ExpressionFactory expressionFactory() {
        return new JQExpressionFactory();
    }
}
