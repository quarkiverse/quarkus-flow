package io.quarkiverse.flow.durable.kube.it;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkiverse.flow.durable.kube.KubeInfoStrategy;
import io.quarkus.runtime.Startup;

@Startup
public class KubeFixtures {

    private static final String APP_LABEL_KEY = "app";
    private static final String APP_LABEL_VAL = "it";

    private static final Logger LOGGER = LoggerFactory.getLogger(KubeFixtures.class);

    @Inject
    KubernetesClient client;

    @Inject
    KubeInfoStrategy kubeInfo;

    @PostConstruct
    void init() {
        String ns = kubeInfo.namespace();

        // ensure namespace exists
        Namespace namespace = client.namespaces().withName(ns).get();
        if (namespace == null) {
            client.namespaces().resource(new NamespaceBuilder()
                    .withNewMetadata().withName(ns).endMetadata()
                    .build()).create();
        }
        ensureDefaultServiceAccount(ns);

        // --- Deployment (valid pod template) ---
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName("it-deploy")
                .withNamespace(ns)
                .endMetadata()
                .withNewSpec()
                .withReplicas(3)
                .withNewSelector()
                .addToMatchLabels(APP_LABEL_KEY, APP_LABEL_VAL)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(APP_LABEL_KEY, APP_LABEL_VAL)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("dummy")
                .withImage("busybox:1.36")
                .withCommand("sh", "-c", "sleep 3600")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        Deployment createdDeploy = client.apps().deployments().inNamespace(ns).resource(deployment).serverSideApply();
        String deployUid = createdDeploy.getMetadata().getUid();

        // --- ReplicaSet (valid pod template + ownerRef with uid) ---
        ReplicaSet rs = new ReplicaSetBuilder()
                .withNewMetadata()
                .withName("it-rs")
                .withNamespace(ns)
                .withOwnerReferences(new OwnerReferenceBuilder()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName(createdDeploy.getMetadata().getName())
                        .withUid(deployUid)
                        .withController(true)
                        .build())
                .endMetadata()
                .withNewSpec()
                .withNewSelector()
                .addToMatchLabels(APP_LABEL_KEY, APP_LABEL_VAL)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(APP_LABEL_KEY, APP_LABEL_VAL)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("dummy")
                .withImage("busybox:1.36")
                .withCommand("sh", "-c", "sleep 3600")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        ReplicaSet createdRs = client.apps().replicaSets().inNamespace(ns).resource(rs).serverSideApply();
        String rsUid = createdRs.getMetadata().getUid();

        // --- Pod (valid + ownerRef with uid) ---
        var pod = new PodBuilder()
                .withNewMetadata()
                .withName(kubeInfo.podName())
                .withNamespace(ns)
                .addToLabels(APP_LABEL_KEY, APP_LABEL_VAL)
                .withOwnerReferences(new OwnerReferenceBuilder()
                        .withApiVersion("apps/v1")
                        .withKind("ReplicaSet")
                        .withName(createdRs.getMetadata().getName())
                        .withUid(rsUid)
                        .withController(true)
                        .build())
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("dummy")
                .withImage("busybox:1.36")
                .withCommand("sh", "-c", "sleep 3600")
                .endContainer()
                .endSpec()
                .build();

        client.pods().inNamespace(ns).resource(pod).serverSideApply();

        LOGGER.info("Mocked deployment object '{}' created successfully in namespace '{}'", deployment.getMetadata().getName(),
                ns);
    }

    private void ensureDefaultServiceAccount(String ns) {
        ServiceAccount sa = client.serviceAccounts().inNamespace(ns).withName("default").get();
        if (sa == null) {
            client.serviceAccounts().inNamespace(ns).resource(new ServiceAccountBuilder()
                    .withNewMetadata()
                    .withName("default")
                    .withNamespace(ns)
                    .endMetadata()
                    .build()).create();
        }
    }
}
