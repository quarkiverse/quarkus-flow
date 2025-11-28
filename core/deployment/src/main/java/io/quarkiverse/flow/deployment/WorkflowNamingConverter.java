package io.quarkiverse.flow.deployment;

import java.util.Objects;
import java.util.Optional;

public interface WorkflowNamingConverter {

    /**
     * Converts a <code>document.namespace</code> from Workflow specification to a Java package name.
     * <p>
     * This method assumes that the provided namespace is valid according to the
     * <a href="https://github.com/serverlessworkflow/specification/blob/main/schema/workflow.yaml">CNCF Specification</a>.
     *
     * @param namespace the CNCF <code>document.namespace</code> to convert
     * @return the corresponding Java package name
     */
    static String namespaceToPackage(String namespace) {
        Objects.requireNonNull(namespace, "'namespace' must not be null");
        return namespace.replace('-', '.').toLowerCase();
    }

    /**
     * Converts a <code>document.name</code> from a Workflow Specification to a Java class name.
     * <p>
     * This method assumes that the provided name is valid according to the
     * <a href="https://github.com/serverlessworkflow/specification/blob/main/schema/workflow.yaml">CNCF Specification</a>.
     * <p>
     * Example:
     * <code>
     * String className = WorkflowNamingConverter.nameToClassName("CNCFWorkflow");
     * Assertions.assertEquals("CNCFWorkflow", className);
     * </code>
     *
     * @param name the CNCF Workflow specification <code>document.name</code> to convert
     * @return the corresponding Java class name
     */
    static String nameToClassName(String name) {
        Objects.requireNonNull(name, "'name' must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("'name' must not be empty");
        }

        StringBuilder classNameBuilder = new StringBuilder(name.length());

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-') {
                continue;
            }
            if (i == 0 || name.charAt(i - 1) == '-') {
                classNameBuilder.append(Character.toUpperCase(c));
            } else {
                classNameBuilder.append(c);
            }
        }

        return classNameBuilder.toString();
    }

    /**
     * Generates a class identifier for {@link io.quarkiverse.flow.Flow} subclasses.
     *
     * @param namespace Document's namespace from specification
     * @param name Document's name from specification
     * @param namespaceFromConfig Base namespace for generating class identifiers
     * @return the generated class identifier
     */
    static String generateFlowClassIdentifier(String namespace, String name, Optional<String> namespaceFromConfig) {
        return namespaceFromConfig.map(s -> String.format("%s.%s.%s", s, namespaceToPackage(namespace), nameToClassName(name)))
                .orElseGet(() -> namespaceToPackage(namespace) + "." + nameToClassName(name));
    }

}
