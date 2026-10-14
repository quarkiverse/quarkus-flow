package io.quarkiverse.flow.internal;

import java.util.Optional;

import io.serverlessworkflow.api.types.SchemaInline;
import io.serverlessworkflow.impl.WorkflowError;
import io.serverlessworkflow.impl.resources.ExternalResourceHandler;
import io.serverlessworkflow.impl.schema.SchemaValidator;
import io.serverlessworkflow.impl.schema.SchemaValidatorFactory;

/**
 * {@link SchemaValidatorFactory} that never rejects anything, used to override the real
 * {@code serverlessworkflow-impl-validation} validator (auto-discovered via {@link java.util.ServiceLoader})
 * when {@code quarkus.flow.validation.input.enabled} is {@code false}.
 */
public class NoOpSchemaValidatorFactory implements SchemaValidatorFactory {

    private static final SchemaValidator NO_OP = model -> Optional.<WorkflowError.Builder> empty();

    @Override
    public SchemaValidator getValidator(SchemaInline schema) {
        return NO_OP;
    }

    @Override
    public SchemaValidator getValidator(ExternalResourceHandler resource) {
        return NO_OP;
    }

}
