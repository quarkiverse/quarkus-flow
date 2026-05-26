package org.acme.langchain4j;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import io.quarkiverse.flow.langchain4j.workflow.builder.FlowAgentsBuilderService;

/**
 * Example demonstrating runtime creation of LangChain4j workflows using FlowAgentsBuilderService.
 * <p>
 * Unlike the annotation-based approach ({@link Agents}), this pattern creates workflows dynamically
 * at runtime. Use this when:
 * <ul>
 * <li>Workflow structure depends on runtime data (user config, database records)</li>
 * <li>Building a workflow editor or no-code platform</li>
 * <li>Creating temporary, one-off agent orchestrations</li>
 * </ul>
 */
@ApplicationScoped
public class DynamicWorkflowExample {

    @Inject
    FlowAgentsBuilderService builderService;

    // tag::sequential[]
    /**
     * Example: Sequential workflow for data processing pipeline.
     */
    public UntypedAgent createDataPipeline() {
        var validateAgent = AgenticServices.agentAction(scope -> {
            String data = scope.readState("input", "");
            boolean valid = data != null && !data.isEmpty();
            scope.writeState("valid", valid);
        });

        var processAgent = AgenticServices.agentAction(scope -> {
            String data = scope.readState("input", "");
            scope.writeState("processed", data.toUpperCase());
        });

        var saveAgent = AgenticServices.agentAction(scope -> {
            String processed = scope.readState("processed", "");
            scope.writeState("saved", true);
            scope.writeState("result", "Saved: " + processed);
        });

        return builderService.newSequential()
                .subAgents(validateAgent, processAgent, saveAgent)
                .build();
    }
    // end::sequential[]

    /**
     * Example: Parallel workflow for concurrent API calls.
     */
    public UntypedAgent createParallelFetcher() {
        var fetchWeather = AgenticServices.agentAction(scope -> {
            // Simulate weather API call
            scope.writeState("weather", "Sunny, 25°C");
        });

        var fetchNews = AgenticServices.agentAction(scope -> {
            // Simulate news API call
            scope.writeState("news", "Breaking: AI advances");
        });

        var fetchStock = AgenticServices.agentAction(scope -> {
            // Simulate stock API call
            scope.writeState("stock", "NASDAQ +1.2%");
        });

        return builderService.newParallel()
                .subAgents(fetchWeather, fetchNews, fetchStock)
                .build();
    }

    /**
     * Example: Loop workflow with retry logic.
     */
    public UntypedAgent createRetryWorkflow(int maxRetries) {
        var attemptTask = AgenticServices.agentAction(scope -> {
            int attempts = scope.readState("attempts", 0);
            scope.writeState("attempts", attempts + 1);

            // Simulate success on 3rd attempt
            boolean success = attempts >= 2;
            scope.writeState("success", success);
        });

        return builderService.newLoop()
                .maxIterations(maxRetries)
                .exitCondition((scope, iteration) -> scope.readState("success", false))
                .subAgents(attemptTask)
                .build();
    }

    /**
     * Example: Conditional workflow for routing based on user type.
     */
    public UntypedAgent createUserRouter() {
        var adminFlow = AgenticServices.agentAction(scope -> {
            scope.writeState("message", "Admin dashboard");
            scope.writeState("permissions", "ALL");
        });

        var userFlow = AgenticServices.agentAction(scope -> {
            scope.writeState("message", "User dashboard");
            scope.writeState("permissions", "READ");
        });

        var guestFlow = AgenticServices.agentAction(scope -> {
            scope.writeState("message", "Login required");
            scope.writeState("permissions", "NONE");
        });

        return builderService.newConditional()
                .subAgents(scope -> "admin".equals(scope.readState("userType", "")), adminFlow)
                .subAgents(scope -> "user".equals(scope.readState("userType", "")), userFlow)
                .subAgents(scope -> true, guestFlow) // Default fallback
                .build();
    }

    /**
     * Execute a workflow and return the result.
     */
    public Map<String, Object> executeWorkflow(UntypedAgent workflow, Map<String, Object> input) {
        ResultWithAgenticScope<String> result = workflow.invokeWithAgenticScope(input);
        return result.agenticScope().state();
    }

    /**
     * Example usage: Dynamic workflow selection based on configuration.
     */
    public String processRequest(String workflowType, Map<String, Object> input) {
        UntypedAgent workflow = switch (workflowType) {
            case "pipeline" -> createDataPipeline();
            case "parallel" -> createParallelFetcher();
            case "retry" -> createRetryWorkflow(5);
            case "router" -> createUserRouter();
            default -> throw new IllegalArgumentException("Unknown workflow type: " + workflowType);
        };

        Map<String, Object> result = executeWorkflow(workflow, input);
        return result.toString();
    }
}
