package io.quarkiverse.flow.durable.kube;

public record MemberLeaseEvent(
        Type type,
        String poolName,
        String podName,
        String leaseName) {
    public enum Type {
        ACQUIRED,
        LOST,
        RELEASED
    }
}
