package org.acme;

import java.time.LocalDate;
import java.util.List;

public record CallForPapers(
        String id,
        String title,
        String conference,
        String description,
        LocalDate deadline,
        List<String> topics,
        String location,
        LocalDate conferenceDate,
        String submissionUrl) {
    public static CallForPapers createMock() {
        return new CallForPapers(
                "CFP-2026-QFLOW-001",
                "Quarkus Flow: Building Reactive Workflows",
                "ServerlessWorkflow Conference 2026",
                "We invite researchers and practitioners to submit papers on innovative approaches to building reactive, event-driven workflows using Quarkus Flow. Topics include workflow orchestration, event-driven architectures, microservices patterns, and observability in distributed systems.",
                LocalDate.now().plusMonths(3),
                List.of(
                        "Reactive Programming",
                        "Event-Driven Architecture",
                        "Workflow Orchestration",
                        "Microservices Patterns",
                        "Observability & Monitoring"),
                "San Francisco, CA",
                LocalDate.now().plusMonths(3),
                "https://serverlessworkflow.io/submit");
    }
}
