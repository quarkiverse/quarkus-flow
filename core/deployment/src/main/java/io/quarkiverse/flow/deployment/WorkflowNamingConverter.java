package io.quarkiverse.flow.deployment;

import java.util.Objects;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

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
     * Converts a <a href="https://semver.org/">Semantic Versioning</a> string to a valid Java package name segment.
     * <p>
     * Rules applied:
     * <ul>
     * <li>Build metadata (everything after {@code +}) is stripped.</li>
     * <li>Dots ({@code .}) and hyphens ({@code -}) are replaced with underscores ({@code _}).</li>
     * <li>A {@code v} prefix is prepended so the result never starts with a digit.</li>
     * </ul>
     * <p>
     * Examples:
     *
     * <pre>
     *   "0.1.0"         → "v0_1_0"
     *   "1.2.3-alpha.1" → "v1_2_3_alpha_1"
     *   "1.0.0+build.1" → "v1_0_0"
     * </pre>
     *
     * @param version a semantic version string (e.g. {@code "1.2.3"})
     * @return a valid, lowercase Java package name segment representing the version
     * @throws NullPointerException if {@code version} is {@code null}
     * @throws IllegalArgumentException if {@code version} is blank
     */
    static String versionToPackage(String version) {
        Objects.requireNonNull(version, "'version' must not be null");
        if (version.isBlank()) {
            throw new IllegalArgumentException("'version' must not be blank");
        }

        // Strip build metadata (SemVer §10: everything after '+')
        int buildMetaIndex = version.indexOf('+');
        String withoutBuildMeta = buildMetaIndex >= 0 ? version.substring(0, buildMetaIndex) : version;

        // Replace dots and hyphens with underscores and lowercase the result
        String sanitized = withoutBuildMeta.replace('.', '_').replace('-', '_').toLowerCase();

        // Prefix with 'v' so the segment is a valid Java identifier (cannot start with a digit)
        return "v" + sanitized;
    }

    /**
     * Generates a class identifier for {@link io.quarkiverse.flow.Flow} subclasses.
     *
     * @param namespace Document's namespace from specification
     * @param name Document's name from specification
     * @return the generated class identifier
     */
    static String generateFlowClassIdentifier(String namespace, String name) {
        return namespaceToPackage(namespace) + "." + nameToClassName(name);
    }

    /**
     * Generates a class identifier for {@link io.quarkiverse.flow.Flow} subclasses with a custom base namespace.
     *
     * @param namespace Document's namespace from specification
     * @param name Document's name from specification
     * @param namespaceFromConfig Base namespace for generating class identifiers
     * @return the generated class identifier
     */
    static String generateFlowClassIdentifier(String namespace, String name, String namespaceFromConfig) {
        return String.format("%s.%s.%s", namespaceFromConfig, namespaceToPackage(namespace), nameToClassName(name));
    }

    static String generateFlowClassIdentifier(WorkflowDefinitionId workflowDefinitionId, String namespace) {
        return String.format("%s.%s.%s", namespace,
                versionToPackage(workflowDefinitionId.version()),
                nameToClassName(workflowDefinitionId.name()));
    }
}
