package io.quarkiverse.flow.producers;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.jackson.schema.JsonSchemaValidatorFactory;
import io.serverlessworkflow.impl.schema.SchemaValidatorFactory;

public class DefaultSchemaValidatorFactoryProducer {

    @Produces
    @Singleton
    @DefaultBean
    SchemaValidatorFactory schemaValidatorFactory() {
        return new JsonSchemaValidatorFactory();
    }

}
