package io.quarkiverse.flow.langchain4j.deployment;

import static java.lang.reflect.Modifier.isStatic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import io.serverlessworkflow.impl.jackson.JsonUtils;

/**
 * Build-time utility to derive a JSON Schema for a workflow's input from a LangChain4j agent method signature.
 * <p>
 * This implementation uses Jandex APIs instead of reflection, allowing schema generation at build-time.
 * <p>
 * The schema describes the {@link AgenticScope} state map, which is what a generated agentic workflow is started
 * with. Because it is <em>derived</em> rather than authored, it must permit everything Java and LangChain4j permit
 * and only be strict where the method signature gives a sound guarantee:
 * <ul>
 * <li>parameters LangChain4j never writes to the state map (the scope itself, {@link InvocationParameters},
 * {@code @MemoryId}, {@code @LoopCounter}, {@code @K}) are omitted;</li>
 * <li>every non-primitive type is nullable, since nothing in a Java signature forbids {@code null};</li>
 * <li>nested properties are never {@code required} because their presence depends on the application's Jackson
 * configuration, which is not visible at build time;</li>
 * <li>collection elements and map values are typed from their declared type argument and are nullable.</li>
 * </ul>
 * <p>
 * An explicit non-null contract ({@code @NotNull} from Jakarta Validation or JetBrains, JSpecify {@code @NonNull}) is
 * the author opting back into strictness: the annotated parameter or nested property is no longer nullable, and a
 * nested one becomes {@code required}.
 */
final class JandexMethodInputJsonSchema {

    private static final DotName MEMORY_ID = DotName.createSimple(MemoryId.class.getName());
    private static final DotName AGENTIC_SCOPE = DotName.createSimple(AgenticScope.class.getName());
    private static final DotName LOOP_COUNTER = DotName.createSimple(LoopCounter.class.getName());
    private static final DotName INVOCATION_PARAMETERS = DotName.createSimple(InvocationParameters.class.getName());
    private static final DotName VARIABLE = DotName.createSimple(V.class.getName());
    private static final DotName TYPED_KEY = DotName.createSimple(K.class.getName());

    private static final DotName JSON_IGNORE = DotName.createSimple(JsonIgnore.class.getName());
    private static final DotName JSON_PROPERTY = DotName.createSimple(JsonProperty.class.getName());

    // Matched by name only, so none of these libraries is needed on the deployment classpath
    private static final Set<DotName> NON_NULL_ANNOTATIONS = Set.of(
            DotName.createSimple("jakarta.validation.constraints.NotNull"),
            DotName.createSimple("org.jspecify.annotations.NonNull"),
            DotName.createSimple("org.jetbrains.annotations.NotNull"));

    private static final DotName OPTIONAL = DotName.createSimple(Optional.class.getName());
    private static final DotName OBJECT = DotName.createSimple(Object.class.getName());
    private static final DotName STRING = DotName.createSimple(String.class.getName());
    private static final DotName CHAR_SEQUENCE = DotName.createSimple(CharSequence.class.getName());
    private static final DotName BOOLEAN = DotName.createSimple(Boolean.class.getName());
    private static final DotName NUMBER = DotName.createSimple(Number.class.getName());
    private static final DotName MAP = DotName.createSimple(Map.class.getName());
    private static final DotName COLLECTION = DotName.createSimple(Collection.class.getName());

    private static final java.util.Set<DotName> BOXED_NUMBER_TYPES = java.util.Set.of(
            DotName.createSimple("java.lang.Byte"),
            DotName.createSimple("java.lang.Short"),
            DotName.createSimple("java.lang.Integer"),
            DotName.createSimple("java.lang.Long"),
            DotName.createSimple("java.lang.Float"),
            DotName.createSimple("java.lang.Double"));

    private JandexMethodInputJsonSchema() {
    }

    /**
     * Builds a JSON Schema (Draft-7-ish) for the method parameters.
     * Root is always an object with properties matching parameter names.
     *
     * @param index the Jandex index for type inspection
     * @param method the agent method
     * @return ObjectNode representing the JSON schema, or null if no parameters
     */
    public static ObjectNode generateSchemaNode(IndexView index, MethodInfo method) {
        List<Type> paramTypes = method.parameterTypes();

        if (paramTypes.isEmpty()) {
            return null;
        }

        ObjectNode root = JsonUtils.object();
        root.put("type", "object");

        ObjectNode properties = JsonUtils.object();
        ArrayNode requiredArray = JsonUtils.array();

        for (int i = 0; i < paramTypes.size(); i++) {
            Type paramType = paramTypes.get(i);

            // Skip parameters LangChain4j never writes into the AgenticScope state map
            if (isFrameworkParameter(index, method, i, paramType)) {
                continue;
            }

            String paramName = stateKeyFor(method, i);

            boolean nullable = isNullable(paramType) && !isParameterDeclaredNonNull(method, i, paramType);
            properties.set(paramName, buildPropertySchema(index, paramType, true, nullable));

            // Optional<T> is the one signature-level statement that a value may legitimately be absent
            if (!isOptional(paramType)) {
                requiredArray.add(paramName);
            }
        }

        if (properties.isEmpty()) {
            return null;
        }

        root.set("properties", properties);
        root.set("required", requiredArray);
        root.put("$schema", "http://json-schema.org/draft-07/schema#");

        return root;
    }

    /**
     * A parameter is "framework" when LangChain4j routes it somewhere other than the state map, so it can never
     * appear in the validated input.
     * <p>
     * {@code @K} parameters are keyed by the {@code TypedKey}'s runtime {@code name()}, which Jandex cannot resolve,
     * so they are omitted too - unlisted properties are simply left unvalidated.
     */
    private static boolean isFrameworkParameter(IndexView index, MethodInfo method, int paramIndex, Type paramType) {
        boolean frameworkType = (paramType.kind() == Type.Kind.CLASS)
                && (paramType.name().equals(AGENTIC_SCOPE) || isAssignableFrom(index, paramType.name(), INVOCATION_PARAMETERS));

        return frameworkType
                || hasParameterAnnotation(method, paramIndex, MEMORY_ID)
                || hasParameterAnnotation(method, paramIndex, LOOP_COUNTER)
                || hasParameterAnnotation(method, paramIndex, TYPED_KEY);
    }

    /**
     * The key this parameter is stored under in the {@link AgenticScope} state map, mirroring LangChain4j's
     * {@code AgenticParameterNameResolver}: {@code @V("x")} wins over the declared parameter name.
     */
    private static String stateKeyFor(MethodInfo method, int paramIndex) {
        return parameterAnnotation(method, paramIndex, VARIABLE)
                .map(AnnotationInstance::value)
                .map(AnnotationValue::asString)
                .filter(name -> !name.isEmpty())
                .or(() -> Optional.ofNullable(method.parameterName(paramIndex)).filter(name -> !name.isEmpty()))
                .orElse("arg" + paramIndex);
    }

    /**
     * Check if a method parameter has a specific annotation.
     */
    private static boolean hasParameterAnnotation(MethodInfo method, int paramIndex, DotName annotation) {
        return parameterAnnotation(method, paramIndex, annotation).isPresent();
    }

    /**
     * Look up a method parameter annotation so its members can be read.
     */
    private static Optional<AnnotationInstance> parameterAnnotation(MethodInfo method, int paramIndex, DotName annotation) {
        return method.annotations()
                .stream()
                .filter(ann -> ann.name().equals(annotation))
                .filter(ann -> ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER)
                .filter(ann -> ann.target().asMethodParameter().position() == paramIndex)
                .findFirst();
    }

    /**
     * Every reference type is nullable: nothing in a Java signature says otherwise. Primitives are the sole
     * exception - they can be neither {@code null} nor absent.
     */
    private static boolean isNullable(Type type) {
        return type.kind() != Type.Kind.PRIMITIVE;
    }

    /**
     * Whether the parameter carries an explicit non-null contract, either as a declaration annotation or as a
     * {@code TYPE_USE} annotation on its type.
     */
    private static boolean isParameterDeclaredNonNull(MethodInfo method, int paramIndex, Type paramType) {
        return NON_NULL_ANNOTATIONS.stream()
                .anyMatch(name -> hasParameterAnnotation(method, paramIndex, name) || paramType.hasAnnotation(name));
    }

    /**
     * Whether the field carries an explicit non-null contract. Only the field itself and its top-level type count, so
     * {@code List<@NonNull String>} does not make the list non-null. Record component annotations reach the field
     * when they target {@code FIELD} or {@code TYPE_USE}.
     */
    private static boolean isFieldDeclaredNonNull(FieldInfo field) {
        return NON_NULL_ANNOTATIONS.stream()
                .anyMatch(name -> field.hasDeclaredAnnotation(name) || field.type().hasAnnotation(name));
    }

    private static boolean isOptional(Type type) {
        return type.name().equals(OPTIONAL);
    }

    private static Type optionalValueType(Type type) {
        return ((type.kind() == Type.Kind.PARAMETERIZED_TYPE) && !type.asParameterizedType().arguments().isEmpty())
                ? type.asParameterizedType().arguments().get(0)
                : ClassType.create(OBJECT);
    }

    /**
     * Writes the {@code type} keyword, widening it to a {@code ["<type>", "null"]} union when the value may be null.
     */
    private static void putType(ObjectNode node, String jsonType, boolean nullable) {
        if (nullable) {
            ArrayNode types = JsonUtils.array();
            types.add(jsonType);
            types.add("null");
            node.set("type", types);
        } else {
            node.put("type", jsonType);
        }
    }

    /**
     * Main entry for property schema generation.
     *
     * @param index the Jandex index
     * @param type type of the property
     * @param expandPojoOrRecord whether we are allowed to expand "one level deeper" for POJO-like classes
     * @param nullable whether {@code null} is a legal value for this property
     */
    private static ObjectNode buildPropertySchema(IndexView index, Type type, boolean expandPojoOrRecord, boolean nullable) {
        ObjectNode node = JsonUtils.object();

        if (type.kind() == Type.Kind.PRIMITIVE) {
            node.put("type", mapPrimitiveToJsonType(type.asPrimitiveType()));
            return node;
        }

        // Optional<T> describes T, always nullable
        if (isOptional(type)) {
            return buildPropertySchema(index, optionalValueType(type), expandPojoOrRecord, true);
        }

        // Array - check before the CLASS check since arrays are not CLASS kind
        if (type.kind() == Type.Kind.ARRAY) {
            putType(node, "array", nullable);
            node.set("items", elementSchema(index, type.asArrayType().constituent()));
            return node;
        }

        // Parameterized types (e.g., List<String>, Map<K,V>)
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName rawTypeName = type.name();
            ClassInfo rawTypeInfo = index.getClassByName(rawTypeName);

            // Check if it's a Collection or Map
            // Note: rawTypeInfo might be null if not indexed, so check name directly too
            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, COLLECTION)) {
                putType(node, "array", nullable);
                node.set("items", elementSchema(index, typeArgument(type, 1, 0)));
                return node;
            }
            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, MAP)) {
                putType(node, "object", nullable);
                node.set("additionalProperties", elementSchema(index, typeArgument(type, 2, 1)));
                return node;
            }

            // Fallback: check by name if not indexed
            String typeNameStr = rawTypeName.toString();
            if (typeNameStr.equals("java.util.List") ||
                    typeNameStr.equals("java.util.ArrayList") ||
                    typeNameStr.equals("java.util.Set") ||
                    typeNameStr.equals("java.util.Collection")) {
                putType(node, "array", nullable);
                node.set("items", elementSchema(index, typeArgument(type, 1, 0)));
                return node;
            }
            if (typeNameStr.equals("java.util.Map") || typeNameStr.equals("java.util.HashMap")) {
                putType(node, "object", nullable);
                node.set("additionalProperties", elementSchema(index, typeArgument(type, 2, 1)));
                return node;
            }

            // Other parameterized types
            putType(node, "object", nullable);
            return node;
        }

        if (type.kind() != Type.Kind.CLASS) {
            // Wildcards, type variables, etc.
            putType(node, "object", nullable);
            return node;
        }

        DotName typeName = type.name();
        ClassInfo classInfo = index.getClassByName(typeName);

        // Enum
        if (classInfo != null && classInfo.isEnum()) {
            putType(node, "string", nullable);
            ArrayNode enumValues = JsonUtils.array();
            for (FieldInfo field : classInfo.fields()) {
                if (field.isEnumConstant()) {
                    enumValues.add(field.name());
                }
            }
            if (nullable) {
                enumValues.addNull();
            }
            node.set("enum", enumValues);
            return node;
        }

        // Collection
        if (isAssignableFrom(index, typeName, COLLECTION)) {
            putType(node, "array", nullable);
            node.set("items", JsonUtils.object());
            return node;
        }

        // Map
        if (isAssignableFrom(index, typeName, MAP)) {
            putType(node, "object", nullable);
            node.set("additionalProperties", JsonUtils.object());
            return node;
        }

        // POJO expansion
        if (expandPojoOrRecord && isPojoLike(index, typeName)) {
            return buildPojoSchema(index, classInfo, nullable);
        }

        putType(node, mapJavaTypeToJsonType(index, type), nullable);
        return node;
    }

    /**
     * Schema for a collection/array element or map value. Derived from the declared element type and always nullable
     * (except primitives), since Java collections accept {@code null} elements. When the type is unknown
     * ({@code Object}, wildcards, type variables) the schema is left open.
     */
    private static ObjectNode elementSchema(IndexView index, Type type) {
        if (type == null) {
            return JsonUtils.object();
        }
        if (type.kind() == Type.Kind.WILDCARD_TYPE) {
            return type.asWildcardType().superBound() == null
                    ? elementSchema(index, type.asWildcardType().extendsBound())
                    : JsonUtils.object();
        }
        if (type.kind() == Type.Kind.TYPE_VARIABLE || type.kind() == Type.Kind.UNRESOLVED_TYPE_VARIABLE
                || type.kind() == Type.Kind.TYPE_VARIABLE_REFERENCE || type.name().equals(OBJECT)) {
            return JsonUtils.object();
        }
        return buildPropertySchema(index, type, false, isNullable(type));
    }

    /**
     * The type argument at {@code position}, only trusted for {@code java.util} containers whose declared type
     * parameters have the standard meaning; {@code null} (open schema) otherwise.
     */
    private static Type typeArgument(Type type, int expectedArguments, int position) {
        if (type.kind() != Type.Kind.PARAMETERIZED_TYPE || !type.name().toString().startsWith("java.util.")) {
            return null;
        }
        List<Type> arguments = type.asParameterizedType().arguments();
        return arguments.size() == expectedArguments ? arguments.get(position) : null;
    }

    /**
     * Build a one-level object schema for records or POJO-ish classes.
     * Nested POJOs are not expanded recursively; they become type: "object".
     * <p>
     * By default no property is {@code required}: whether a property is serialized at all depends on the
     * application's Jackson configuration ({@code @JsonInclude} such as {@code NON_DEFAULT}, which can omit even
     * primitives left at their default, naming strategies, programmatic customizers), none of which a build-time
     * Jandex pass can see.
     * <p>
     * A reference-typed property with an explicit non-null contract is the exception: it is non-nullable and
     * {@code required}, since a non-null value is serialized under the default inclusion. {@code Optional} and
     * primitive properties are left as they are - an empty {@code Optional} is still {@code null} on the wire, and
     * {@code NON_DEFAULT} may still omit a primitive.
     */
    private static ObjectNode buildPojoSchema(IndexView index, ClassInfo pojoType, boolean nullable) {
        ObjectNode root = JsonUtils.object();
        putType(root, "object", nullable);

        ObjectNode properties = JsonUtils.object();
        ArrayNode requiredArray = JsonUtils.array();

        for (FieldInfo field : pojoType.fields()) {
            if (isStatic(field.flags()) || field.hasAnnotation(JSON_IGNORE)) {
                continue;
            }
            String fieldName = jsonNameOf(field);
            Type fieldType = field.type();

            boolean strict = isNullable(fieldType) && !isOptional(fieldType) && isFieldDeclaredNonNull(field);

            ObjectNode fieldSchema = buildPropertySchema(index, fieldType, false, isNullable(fieldType) && !strict);
            properties.set(fieldName, fieldSchema);

            if (strict) {
                requiredArray.add(fieldName);
            }
        }

        if (!properties.isEmpty()) {
            root.set("properties", properties);
        }
        if (!requiredArray.isEmpty()) {
            root.set("required", requiredArray);
        }

        return root;
    }

    /**
     * The name this field is serialized under, honoring an explicit {@code @JsonProperty}.
     */
    private static String jsonNameOf(FieldInfo field) {
        return Optional.ofNullable(field.annotation(JSON_PROPERTY))
                .map(AnnotationInstance::value)
                .map(AnnotationValue::asString)
                .filter(name -> !name.isEmpty())
                .orElseGet(field::name);
    }

    /**
     * Decide if a class is "POJO-like enough" to expand one level.
     */
    private static boolean isPojoLike(IndexView index, DotName typeName) {
        if (typeName.equals(STRING) || isAssignableFrom(index, typeName, CHAR_SEQUENCE)) {
            return false;
        }
        if (typeName.equals(BOOLEAN)) {
            return false;
        }
        if (BOXED_NUMBER_TYPES.contains(typeName) || isAssignableFrom(index, typeName, NUMBER)) {
            return false;
        }
        if (isAssignableFrom(index, typeName, MAP)) {
            return false;
        }
        if (isAssignableFrom(index, typeName, COLLECTION)) {
            return false;
        }

        ClassInfo classInfo = index.getClassByName(typeName);
        return classInfo != null && !classInfo.isEnum();
    }

    /**
     * Type-mapping helper for JSON Schema types.
     */
    private static String mapJavaTypeToJsonType(IndexView index, Type type) {
        if (type.kind() == Type.Kind.PRIMITIVE) {
            return mapPrimitiveToJsonType(type.asPrimitiveType());
        }

        // Array - check before the CLASS check since arrays are not CLASS kind
        if (type.kind() == Type.Kind.ARRAY) {
            return "array";
        }

        // Parameterized types (e.g., List<String>, Map<K,V>)
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName rawTypeName = type.name();
            ClassInfo rawTypeInfo = index.getClassByName(rawTypeName);

            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, COLLECTION)) {
                return "array";
            }
            if (rawTypeInfo != null && isAssignableFrom(index, rawTypeName, MAP)) {
                return "object";
            }

            // Fallback: check by name if not indexed
            String typeNameStr = rawTypeName.toString();
            if (typeNameStr.equals("java.util.List") ||
                    typeNameStr.equals("java.util.ArrayList") ||
                    typeNameStr.equals("java.util.Set") ||
                    typeNameStr.equals("java.util.Collection")) {
                return "array";
            }
            if (typeNameStr.equals("java.util.Map") || typeNameStr.equals("java.util.HashMap")) {
                return "object";
            }

            return "object";
        }

        if (type.kind() != Type.Kind.CLASS) {
            return "object";
        }

        DotName typeName = type.name();

        if (typeName.equals(STRING) || isAssignableFrom(index, typeName, CHAR_SEQUENCE)) {
            return "string";
        }

        if (typeName.equals(BOOLEAN)) {
            return "boolean";
        }

        if (BOXED_NUMBER_TYPES.contains(typeName) || isAssignableFrom(index, typeName, NUMBER)) {
            return "number";
        }

        if (isAssignableFrom(index, typeName, COLLECTION)) {
            return "array";
        }

        if (isAssignableFrom(index, typeName, MAP)) {
            return "object";
        }

        ClassInfo classInfo = index.getClassByName(typeName);
        if (classInfo != null && classInfo.isEnum()) {
            return "string";
        }

        return "object";
    }

    private static String mapPrimitiveToJsonType(PrimitiveType primitive) {
        return switch (primitive.primitive()) {
            case BOOLEAN -> "boolean";
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> "number";
            case CHAR -> "string";
        };
    }

    /**
     * Simple check if type is assignable from a given interface/superclass.
     */
    private static boolean isAssignableFrom(IndexView index, DotName type, DotName superType) {
        if (type.equals(superType)) {
            return true;
        }

        ClassInfo classInfo = index.getClassByName(type);
        if (classInfo == null) {
            return false;
        }

        // Check interfaces
        for (Type iface : classInfo.interfaceTypes()) {
            if (iface.name().equals(superType) || isAssignableFrom(index, iface.name(), superType)) {
                return true;
            }
        }

        // Check superclass
        Type superClass = classInfo.superClassType();
        if (superClass != null && !superClass.name().equals(DotName.createSimple("java.lang.Object"))) {
            return isAssignableFrom(index, superClass.name(), superType);
        }

        return false;
    }
}
