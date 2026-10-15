package io.quarkiverse.flow.testing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QuarkusFlowTest {

    Scope scope() default Scope.PER_METHOD;

    enum Scope {

        /**
         * Each test method gets its own isolated recording session: the {@link WorkflowEventStore}
         * is cleared immediately before the method runs.
         */
        PER_METHOD,

        /**
         * All test methods in the class share a single recording session: the
         * {@link WorkflowEventStore} is cleared once before the first method runs and events from
         * every method accumulate in the same buffer until the class finishes.
         */
        PER_CLASS
    }
}
