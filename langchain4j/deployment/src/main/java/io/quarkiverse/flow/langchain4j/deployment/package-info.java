/**
 * Build-time processing for LangChain4j agentic workflow generation.
 * <p>
 * This package contains the Quarkus build-time processors and helpers that generate
 * {@link io.quarkiverse.flow.langchain4j.workflow.AgenticFlow} implementations from
 * LangChain4j agent annotations.
 *
 * <h2>Build Process</h2>
 * <ol>
 * <li>{@link io.quarkiverse.flow.langchain4j.deployment.FlowLangChain4jProcessor#collectAgenticWorkflows} -
 * Scans for LangChain4j agent annotations</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.deployment.AgenticWorkflowBlueprint#fromAgenticMethod} -
 * Extracts workflow metadata</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.deployment.FlowLangChain4jProcessor#generateAgenticFlowClasses} -
 * Generates AgenticFlow classes using Gizmo</li>
 * </ol>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link io.quarkiverse.flow.langchain4j.deployment.FlowLangChain4jProcessor} - Main build processor</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.deployment.GizmoAgentFlowsHelper} - Bytecode generation helpers</li>
 * <li>{@link io.quarkiverse.flow.langchain4j.deployment.AgenticWorkflowBlueprint} - Workflow metadata representation</li>
 * </ul>
 *
 * @see io.quarkiverse.flow.langchain4j.workflow
 */
package io.quarkiverse.flow.langchain4j.deployment;
