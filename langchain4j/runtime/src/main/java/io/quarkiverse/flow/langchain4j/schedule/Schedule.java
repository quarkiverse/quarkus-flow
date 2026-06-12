package io.quarkiverse.flow.langchain4j.schedule;

import java.util.stream.Stream;

public record Schedule(String cron, String event, String every) {

    public Schedule {
        long count = Stream.of(cron, event, every)
                .filter(Schedule::isPresent)
                .count();
        if (count != 1) {
            throw new IllegalStateException(
                    "Exactly one schedule strategy must be configured (cron, event, or every), but found: " + count);
        }
    }

    public ScheduleType scheduleType() {
        if (isPresent(cron))
            return ScheduleType.CRON;
        if (isPresent(every))
            return ScheduleType.EVERY;
        return ScheduleType.EVENT; // the constructor guarantees exactly one is set
    }

    public String value() {
        return switch (scheduleType()) {
            case CRON -> cron;
            case EVERY -> every;
            case EVENT -> event;
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
