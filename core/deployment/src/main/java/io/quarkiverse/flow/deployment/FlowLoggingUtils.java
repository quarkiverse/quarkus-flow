package io.quarkiverse.flow.deployment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;

/**
 * Small helper for pretty, table-style logging of lists of identifiers.
 */
public final class FlowLoggingUtils {
    private FlowLoggingUtils() {
        // utility
    }

    /**
     * Logs a table-style list of identifiers.
     *
     * @param log logger to use
     * @param identifiers items to log
     * @param noneMessage log line when the list is empty
     * @param titleLine line before the table (e.g. "Flow: Registered ...")
     * @param header header text for the single table column
     */
    public static void logWorkflowList(Logger log,
            List<String> identifiers,
            String noneMessage,
            String titleLine,
            String header) {

        if (identifiers == null || identifiers.isEmpty()) {
            log.info(noneMessage);
            return;
        }

        // sort for stable output, the list might be immutable.
        List<String> sorted = new ArrayList<>(identifiers);
        Collections.sort(sorted);

        int w = header.length();
        for (String s : sorted) {
            w = Math.max(w, s.length());
        }

        String sep = "+" + "-".repeat(w + 2) + "+";
        StringBuilder sb = new StringBuilder(64 + sorted.size() * (w + 8));
        sb.append('\n');
        sb.append(titleLine).append('\n');
        sb.append(sep).append('\n');
        sb.append(String.format("| %-" + w + "s |\n", header));
        sb.append(sep).append('\n');
        for (String s : sorted) {
            sb.append(String.format("| %-" + w + "s |\n", s));
        }
        sb.append(sep).append('\n');

        log.info(sb.toString());
    }
}
