package io.quarkiverse.flow.deployment;

import jakarta.inject.Inject;

import org.objectweb.asm.Opcodes;

import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

interface GizmoFlowHelper {

    static FieldCreator addWorkflowDefinitionField(ClassCreator creator, String identifier) {
        FieldCreator fieldCreator = creator.getFieldCreator("workflowDefinition",
                WorkflowDefinition.class.getName());
        fieldCreator.setModifiers(Opcodes.ACC_PUBLIC);
        fieldCreator.addAnnotation(Inject.class);
        fieldCreator.addAnnotation(Identifier.class)
                .add("value", identifier);
        return fieldCreator;
    }

    static void addIdentifierMethod(ClassCreator creator, String identifier) {
        var identifierMethod = creator.getMethodCreator("identifier", String.class);
        identifierMethod.setModifiers(Opcodes.ACC_PUBLIC);
        identifierMethod.returnValue(identifierMethod.load(identifier));
    }

    static void addDescriptorMethod(ClassCreator creator, FieldCreator fieldCreator) {
        var descriptor = creator.getMethodCreator("descriptor", Workflow.class);
        descriptor.setModifiers(Opcodes.ACC_PUBLIC);
        descriptor.returnValue(
                descriptor.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(WorkflowDefinition.class, "workflow", Workflow.class),
                        descriptor.readInstanceField(fieldCreator.getFieldDescriptor(), descriptor.getThis())));
    }
}
