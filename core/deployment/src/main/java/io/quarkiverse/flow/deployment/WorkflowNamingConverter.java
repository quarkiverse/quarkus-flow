package io.quarkiverse.flow.deployment;

import java.util.Objects;

public interface WorkflowNamingConverter {

    /**
     * Converts a <code>document.namespace</code> from Workflow specification to a Java package name,
     * using the given base namespace as prefix.
     * <p>
     * This method assumes that the provided namespace is valid according to the
     * <a href="https://github.com/serverlessworkflow/specification/blob/main/schema/workflow.yaml">CNCF Specification</a>.
     *
     * @param prefix the base namespace to use as prefix
     * @param namespace the CNCF <code>document.namespace</code> to convert
     * @return the corresponding Java package name
     */
    static String namespaceToPackage(String prefix, String namespace) {
        Objects.requireNonNull(namespace, "'baseNamespace' must not be null");
        String packageName = namespace.replace('-', '.').toLowerCase();
        return prefix + "." + packageName;
    }

    /**
     * Converts a <code>document.name</code> from a Workflow Specification to a Java class name.
     * <p>
     * This method assumes that the provided name is valid according to the
     * <a href="https://github.com/serverlessworkflow/specification/blob/main/schema/workflow.yaml">CNCF Specification</a>.
     * <p>
     * Example:
     * <code>
     * String className = Identifiers.nameToClassName("CNCFWorkflow");
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

        StringBuilder classNameBuilder = new StringBuilder();
        boolean upperNext = true;

        for (char c : name.toCharArray()) {
            if (c == '-') {
                upperNext = true;
            } else {
                if (upperNext) {
                    classNameBuilder.append(Character.toUpperCase(c));
                    upperNext = false;
                } else {
                    classNameBuilder.append(c);
                }
            }
        }

        return classNameBuilder.toString();
    }

    /**
     * Generates a class identifier for {@link io.quarkiverse.flow.Flow} subclasses.
     *
     * @param namespace Document's namespace from specification
     * @param name Document's name from specification
     * @param prefix Base namespace for generating class identifiers
     * @return the generated class identifier
     */
    static String generateFlowClassIdentifier(String namespace, String name, String prefix) {
        return namespaceToPackage(prefix, namespace) + "." + nameToClassName(name) + "Workflow";
    }

}
