package io.quarkiverse.flow.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.flow.Flowable;
import io.smallrye.common.annotation.Identifier;

public class DotNames {

    public static final DotName FLOWABLE = DotName.createSimple(Flowable.class.getName());
    public static final DotName IDENTIFIER = DotName.createSimple(Identifier.class.getName());

}
