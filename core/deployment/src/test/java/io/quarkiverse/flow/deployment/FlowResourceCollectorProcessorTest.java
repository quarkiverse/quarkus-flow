package io.quarkiverse.flow.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkus.deployment.ApplicationArchive;
import io.quarkus.deployment.ApplicationArchiveImpl;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;

class FlowResourceCollectorProcessorTest {

    @Test
    @DisplayName("direct_dependency_is_eligible_for_workflow_discovery")
    void direct_dependency_is_eligible_for_workflow_discovery() {
        assertThat(FlowResourceCollectorProcessor.isDirectDependency(archive(dependency("workflow-library", true))))
                .isTrue();
    }

    @Test
    @DisplayName("transitive_dependency_is_not_eligible_for_workflow_discovery")
    void transitive_dependency_is_not_eligible_for_workflow_discovery() {
        assertThat(FlowResourceCollectorProcessor.isDirectDependency(archive(dependency("workflow-library", false))))
                .isFalse();
    }

    @Test
    @DisplayName("archive_without_dependency_metadata_is_eligible_for_workflow_discovery")
    void archive_without_dependency_metadata_is_eligible_for_workflow_discovery() {
        assertThat(FlowResourceCollectorProcessor.isDirectDependency(archive(null))).isTrue();
    }

    @Test
    @DisplayName("dependency_archive_id_is_path_safe_and_unique_per_artifact")
    void dependency_archive_id_is_path_safe_and_unique_per_artifact() {
        assertThat(FlowResourceCollectorProcessor.dependencyArchiveId(archive(dependency("workflow-library", true)), 1))
                .isEqualTo("org.acme/workflow-library");
        assertThat(FlowResourceCollectorProcessor.dependencyArchiveId(archive(null), 3))
                .isEqualTo("archive-3");
    }

    private static ResolvedDependency dependency(String artifactId, boolean direct) {
        return ResolvedDependencyBuilder.newInstance()
                .setGroupId("org.acme")
                .setArtifactId(artifactId)
                .setVersion("1.0.0")
                .setDirect(direct)
                .build();
    }

    private static ApplicationArchive archive(ResolvedDependency dependency) {
        return new ApplicationArchiveImpl(null, null, dependency);
    }
}
