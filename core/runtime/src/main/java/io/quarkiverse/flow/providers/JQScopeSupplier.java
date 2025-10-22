package io.quarkiverse.flow.providers;

import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import net.thisptr.jackson.jq.Scope;

@Singleton
public class JQScopeSupplier implements Supplier<Scope> {

    @Inject
    Scope scope; // provided by quarkus-jackson-jq

    @Override
    public Scope get() {
        return scope;
    }

}
