package io.quarkiverse.flow;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method that returns a Workflow descriptor.
 * Optional value = the workflow id (qualifier) to use.
 * If empty, we fall back to the method name.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface FlowDescriptor {
    String value() default "";
}
