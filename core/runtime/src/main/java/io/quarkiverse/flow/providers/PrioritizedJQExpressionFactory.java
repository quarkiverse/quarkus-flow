package io.quarkiverse.flow.providers;

import java.util.function.Supplier;

import io.serverlessworkflow.impl.expressions.ExpressionDescriptor;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import net.thisptr.jackson.jq.Scope;

/**
 * A {@link JQExpressionFactory} that takes precedence over the SDK's default
 * implementation by assigning it a higher priority.
 *
 * <p>
 * This factory aims to use the {@link Scope} provided by the Quarkus
 * {@code quarkus-jackson-jq} extension to ensure that JQ expressions use
 * the appropriate evaluation scope.
 *
 * <p>
 * Lower priority values take precedence. This implementation subtracts
 * {@code 100} from the default priority to ensure it is preferred over the
 * SDK-provided {@link JQExpressionFactory}.
 */
public class PrioritizedJQExpressionFactory extends JQExpressionFactory {

    public PrioritizedJQExpressionFactory(Supplier<Scope> scopeSupplier) {
        super(scopeSupplier);
    }

    @Override
    public int priority(ExpressionDescriptor desc) {
        return super.priority(desc) - 100;
    }
}