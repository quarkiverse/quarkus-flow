package io.quarkiverse.flow.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

/**
 * see <a href="https://github.com/serverlessworkflow/sdk-java/issues/812">Native-image build fails due to UlidCreator static
 * initialization (Random in image heap)</a>
 */
final class FlowNativeProcessor {
    @BuildStep
    RuntimeInitializedClassBuildItem ulidCreatorHolder() {
        return new RuntimeInitializedClassBuildItem("com.github.f4b6a3.ulid.UlidCreator$MonotonicFactoryHolder");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem ulidCreator() {
        return new RuntimeInitializedClassBuildItem("com.github.f4b6a3.ulid.UlidCreator");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem ulidFactory() {
        return new RuntimeInitializedClassBuildItem("com.github.f4b6a3.ulid.UlidFactory");
    }

}
