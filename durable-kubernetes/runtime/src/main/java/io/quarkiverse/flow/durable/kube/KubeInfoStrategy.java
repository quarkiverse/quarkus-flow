package io.quarkiverse.flow.durable.kube;

public interface KubeInfoStrategy {

    String namespace();

    String podName();

}
