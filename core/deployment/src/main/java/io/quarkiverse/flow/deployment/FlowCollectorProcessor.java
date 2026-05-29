package io.quarkiverse.flow.deployment;

import java.lang.reflect.Modifier;

import org.jboss.jandex.ClassInfo;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

/**
 * Processor responsible for discovering Workflow definitions from Java classes (Flow implementations).
 */
public class FlowCollectorProcessor {

    /**
     * Collect all beans that implement the {@link io.quarkiverse.flow.Flowable} interface.
     */
    @BuildStep
    void collectFlows(CombinedIndexBuildItem index, BuildProducer<DiscoveredWorkflowBuildItem> wf) {
        for (ClassInfo flow : index.getIndex().getAllKnownImplementations(DotNames.FLOWABLE)) {
            if (flow.isInterface() || Modifier.isAbstract(flow.flags()) || flow.hasAnnotation(DotNames.VETOED)) {
                continue;
            }
            wf.produce(DiscoveredWorkflowBuildItem.fromSource(flow.name().toString()));
        }
    }
}
