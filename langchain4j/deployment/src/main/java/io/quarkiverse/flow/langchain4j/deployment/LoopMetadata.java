package io.quarkiverse.flow.langchain4j.deployment;

import java.util.Optional;

public record LoopMetadata(int maxIterations, Optional<PredicateMetadata> exitCondition, boolean testExitAtLoopEnd) {
}
