package io.quarkiverse.flow.dsl;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.serverlessworkflow.api.types.OpenAPIArguments;
import io.serverlessworkflow.fluent.spec.configurers.AuthenticationConfigurer;
import io.serverlessworkflow.fluent.spec.spi.CallOpenAPITaskFluent;

public class FuncCallOpenAPIStep extends Step<FuncCallOpenAPIStep, FuncCallOpenAPITaskBuilder> {

    private final List<Consumer<CallOpenAPITaskFluent<?>>> steps = new ArrayList<>();

    private String name;

    public FuncCallOpenAPIStep(String name) {
        this.name = name;
    }

    public FuncCallOpenAPIStep() {
    }

    public void setName(String name) {
        this.name = name;
    }

    public FuncCallOpenAPIStep document(String uri) {
        steps.add(b -> b.document(uri));
        return this;
    }

    public FuncCallOpenAPIStep document(
            String uri, AuthenticationConfigurer authenticationConfigurer) {
        steps.add(b -> b.document(uri, authenticationConfigurer));
        return this;
    }

    public FuncCallOpenAPIStep document(URI uri) {
        steps.add(b -> b.document(uri));
        return this;
    }

    public FuncCallOpenAPIStep document(URI uri, AuthenticationConfigurer authenticationConfigurer) {
        steps.add(b -> b.document(uri, authenticationConfigurer));
        return this;
    }

    public FuncCallOpenAPIStep operation(String operationId) {
        steps.add(b -> b.operation(operationId));
        return this;
    }

    public FuncCallOpenAPIStep parameters(Map<String, Object> params) {
        steps.add(b -> b.parameters(params));
        return this;
    }

    public FuncCallOpenAPIStep parameter(String name, String value) {
        steps.add(b -> b.parameter(name, value));
        return this;
    }

    public FuncCallOpenAPIStep redirect(boolean redirect) {
        steps.add(b -> b.redirect(redirect));
        return this;
    }

    public FuncCallOpenAPIStep authentication(AuthenticationConfigurer authenticationConfigurer) {
        steps.add(b -> b.authentication(authenticationConfigurer));
        return this;
    }

    public FuncCallOpenAPIStep output(OpenAPIArguments.WithOpenAPIOutput output) {
        steps.add(b -> b.output(output));
        return this;
    }

    @Override
    protected void configure(
            FuncTaskItemListBuilder list, Consumer<FuncCallOpenAPITaskBuilder> post) {
        list.openapi(
                name,
                builder -> {
                    for (Consumer<CallOpenAPITaskFluent<?>> c : steps) {
                        c.accept(builder);
                    }
                    post.accept(builder);
                });
    }
}
