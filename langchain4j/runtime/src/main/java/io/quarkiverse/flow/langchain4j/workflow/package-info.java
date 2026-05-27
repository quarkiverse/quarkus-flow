/**
 * Runtime support for LangChain4j agentic workflows.
 * <p>
 * This package provides the integration between LangChain4j's agentic framework and Quarkus Flow's
 * workflow engine. It enables declarative agent orchestration using LangChain4j annotations that are
 * transformed into executable workflows at build-time.
 *
 * <h2>Key Components</h2>
 * <ul>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.AgenticFlow} - Base class for all generated workflows</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.FlowPlanner} - Executes workflows and coordinates agent
 * invocations</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.FlowAgentsBuilderService} - CDI bridge for accessing generated
 * flows</li>
 * </ul>
 *
 * <h2>Supported Topologies</h2>
 * <ul>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.SequentialAgenticFlow} - Sequential agent execution</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.ParallelAgenticFlow} - Parallel agent execution</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.LoopAgenticFlow} - Loop-based agent execution</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.workflow.ConditionalAgenticFlow} - Conditional routing between agents</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>
 * Annotate your agent interfaces with LangChain4j topology annotations:
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;RegisterAiService
 *     public interface StoryWriter {
 *         @SequenceAgent(subAgents = { Writer.class, Editor.class })
 *         String writeStory(String topic);
 *     }
 * }
 * </pre>
 *
 * At build-time, a {@code GeneratedStoryWriterFlow} class is created that implements the workflow logic.
 *
 * @see dev.langchain4j.agentic.declarative
 */
package io.quarkiverse.flow.langchain4j.workflow;
