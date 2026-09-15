package io.quarkiverse.flow.langchain4j.it;

import java.util.List;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.UserMessage;

/**
 * Test agent for verifying AgenticScope persistence with custom domain objects.
 * The domain types in method signatures (TripItinerary, Activity, CostEstimate) should be
 * automatically registered by quarkus-langchain4j for AgenticScope deserialization.
 * <p>
 * Note: All three types must appear directly in method signatures for auto-registration.
 * Nested types (like Activity/CostEstimate inside TripItinerary) are NOT automatically
 * registered unless they also appear as top-level method parameters or return types.
 */
public interface TripPlannerAgent {

    @UserMessage("""
            You are a travel planning expert.
            Create a trip itinerary to any where, any time.
            Include suggested activities and cost estimate.
            Return a simple JSON with destination, days, activities list, and cost.
            """)
    @Agent(description = "Plan a trip with activities and cost estimate", outputKey = "itinerary")
    ResultWithAgenticScope<TripItinerary> planTrip();

    // These methods exist solely to expose nested types in method signatures
    // so quarkus-langchain4j auto-registers them for AgenticScope deserialization
    @Agent(description = "Dummy method to register Activity type", outputKey = "activity")
    Activity registerActivityType();

    @Agent(description = "Dummy method to register CostEstimate type", outputKey = "cost")
    CostEstimate registerCostEstimateType();

    record TripItinerary(String destination, int durationDays, List<Activity> activities, CostEstimate cost) {
    }

    record Activity(String name, String time) {
    }

    record CostEstimate(double totalCost, String currency) {
    }
}
