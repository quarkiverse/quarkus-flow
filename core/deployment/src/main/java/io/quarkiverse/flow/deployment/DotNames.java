package io.quarkiverse.flow.deployment;

import jakarta.enterprise.inject.Vetoed;

import org.jboss.jandex.DotName;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.Flowable;
import io.smallrye.common.annotation.Identifier;

public class DotNames {

    public static final DotName FLOW = DotName.createSimple(Flow.class.getName());
    public static final DotName FLOWABLE = DotName.createSimple(Flowable.class.getName());
    public static final DotName IDENTIFIER = DotName.createSimple(Identifier.class.getName());
    public static final DotName VETOED = DotName.createSimple(Vetoed.class.getName());
}
