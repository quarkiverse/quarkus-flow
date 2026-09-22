package io.quarkiverse.flow.durable.kube;

import java.time.Duration;

public class LeaseAcquisitionException extends Exception {
    private static final long serialVersionUID = 1L;

    public LeaseAcquisitionException(Throwable cause, Duration timeout) {
        super("Failed to acquire Kubernetes lease after PT" + timeout.getSeconds()
                + "S. This pod cannot participate in the durable workflow pool. Exiting to allow Kubernetes to restart with a fresh lease attempt.",
                cause);
    }
}
