package io.quarkiverse.flow.devui;

import java.time.Instant;
import java.util.Comparator;

/**
 * Utility class providing comparators for FlowInstance sorting.
 */
public final class FlowInstanceComparators {

    private FlowInstanceComparators() {
    }

    /**
     * Returns a comparator for the given sort order.
     *
     * @param sort the sort order
     * @return a comparator for FlowInstance
     */
    public static Comparator<FlowInstance> forSort(WorkflowInstanceStore.Sort sort) {
        return switch (sort) {
            case START_TIME_ASC -> comparing(FlowInstance::getStartTime, false);
            case START_TIME_DESC -> comparing(FlowInstance::getStartTime, true);
            case LAST_UPDATE_ASC -> comparing(FlowInstance::getLastUpdateTime, false);
            case LAST_UPDATE_DESC -> comparing(FlowInstance::getLastUpdateTime, true);
            case STATUS_ASC -> comparingStatus(false);
            case STATUS_DESC -> comparingStatus(true);
        };
    }

    private static Comparator<FlowInstance> comparing(
            java.util.function.Function<FlowInstance, Instant> extractor, boolean reversed) {
        Comparator<FlowInstance> comparator = Comparator.comparing(extractor, Comparator.nullsFirst(Comparator.naturalOrder()));
        return reversed ? comparator.reversed() : comparator;
    }

    private static Comparator<FlowInstance> comparingStatus(boolean reversed) {
        Comparator<FlowInstance> comparator = Comparator.comparing(
                fi -> fi.getStatus() != null ? fi.getStatus().name() : "");
        return reversed ? comparator.reversed() : comparator;
    }
}
