package io.quarkiverse.flow.langchain4j.recorders;

import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.langchain4j.schema.MethodInputJsonSchema;
import io.quarkiverse.flow.langchain4j.workflow.AbstractFlowAgentService;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

@Recorder
public class FlowLangChain4jWorkflowRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(FlowLangChain4jWorkflowRecorder.class);

    /**
     * Register a "placeholder" workflow for each LC4J agentic method so that
     * DevUI can see and invoke them even before any agent service is actually used.
     */
    public void registerAgenticWorkflows(List<AgenticWorkflowDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return;
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        WorkflowRegistry registry = Arc.container().instance(WorkflowRegistry.class).get();

        for (AgenticWorkflowDescriptor d : descriptors) {
            try {
                Class<?> iface = Class.forName(d.ifaceName(), true, cl);
                Method method = resolveMethod(iface, d.methodName(), d.parameterTypeNames());
                if (method == null) {
                    continue;
                }

                // Use same name strategy as AbstractFlowAgentService
                WorkflowDefinitionId id = WorkflowNameUtils.newId(iface);

                Workflow wf = new Workflow().withDocument(new Document()
                        .withName(id.name())
                        .withNamespace(id.namespace())
                        .withVersion(id.version())
                        .withSummary("LC4J agent workflow for " + iface.getSimpleName() + "." + method.getName()));

                // Derive JSON input schema from the agent method
                MethodInputJsonSchema.applySchemaIfAbsent(wf, method);

                // Attach bean invoker metadata so DevUI can invoke via CDI bean
                WorkflowInvocationMetadata.setBeanInvoker(wf, iface, method,
                        AbstractFlowAgentService.INVOKER_KIND_AGENTIC_LC4J);

                registry.cacheDescriptor(wf);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Registered placeholder LC4J workflow {} for {}#{}", id.name(), iface.getName(),
                            method.getName());
                }
            } catch (ClassNotFoundException e) {
                LOG.warn("Failed to load agent interface '{}' for LC4J workflow registration", d.ifaceName(), e);
            } catch (RuntimeException e) {
                LOG.warn("Failed to register placeholder LC4J workflow for {}#{}", d.ifaceName(), d.methodName(), e);
            }
        }
    }

    /**
     * Simple reflective resolution: processor already filtered to agentic workflow methods.
     */
    private Method resolveMethod(Class<?> iface, String methodName, List<String> parameterTypeNames) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?>[] paramTypes = new Class<?>[parameterTypeNames.size()];

        try {
            for (int i = 0; i < parameterTypeNames.size(); i++) {
                paramTypes[i] = Class.forName(parameterTypeNames.get(i), true, cl);
            }
            return iface.getMethod(methodName, paramTypes);
        } catch (Exception e) {
            LOG.debug("Failed to find method {}({}) on class {}",
                    methodName, String.join(", ", parameterTypeNames), iface.getName(), e);
            return null;
        }
    }
}
