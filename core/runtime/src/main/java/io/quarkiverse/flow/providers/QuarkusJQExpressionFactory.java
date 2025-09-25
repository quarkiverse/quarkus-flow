package io.quarkiverse.flow.providers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.serverlessworkflow.impl.expressions.ExpressionUtils;
import io.serverlessworkflow.impl.expressions.ObjectExpression;
import io.serverlessworkflow.impl.expressions.jq.JQExpression;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

@ApplicationScoped
@DefaultBean
public class QuarkusJQExpressionFactory extends JQExpressionFactory {

    @Inject
    Scope scope; // provided by quarkus-jackson-jq

    public QuarkusJQExpressionFactory() {
    }

    @Override
    public ObjectExpression buildExpression(String expression) {
        try {
            return new JQExpression(() -> scope, ExpressionUtils.trimExpr(expression), Versions.JQ_1_6);
        } catch (JsonQueryException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
