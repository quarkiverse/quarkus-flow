package io.quarkiverse.flow.testing.assertions;

/**
 * Configurable assertions interface that allows setting up assertion behavior
 * before executing assertions.
 */
public interface ConfigurableAssertions extends WorkflowAssertions {

    /**
     * Enables strict ordering mode for assertions.
     *
     * @return {@link ConfigurableAssertions} for further configurations
     */
    ConfigurableAssertions strictly();

    /**
     * Filters events to only include those for the specified workflow instance.
     *
     * @param id the workflow instance ID to filter by
     * @return {@link ConfigurableAssertions} for further configurations
     */
    ConfigurableAssertions filteringBy(String id);

    ConfigurableAssertions reset();

}