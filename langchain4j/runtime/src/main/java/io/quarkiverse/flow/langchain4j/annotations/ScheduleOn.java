package io.quarkiverse.flow.langchain4j.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a schedule trigger for a LangChain4J agentic workflow method annotated with
 * {@link dev.langchain4j.agentic.declarative.SequenceAgent}, {@link dev.langchain4j.agentic.declarative.ParallelAgent},
 * {@link dev.langchain4j.agentic.declarative.LoopAgent}, or {@link dev.langchain4j.agentic.declarative.ConditionalAgent}.
 * <p>
 * Exactly one strategy (event, cron or every) must be configured. Setting none, or more than one,
 * fails the build.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScheduleOn {

    /**
     * One CloudEvent type filter that trigger this workflow.
     */
    String event() default "";

    /**
     * A cron expression that triggers this workflow on a time schedule.
     */
    String cron() default "";

    /**
     * An ISO 8601 duration that triggers this workflow periodically.
     */
    String every() default "";
}
