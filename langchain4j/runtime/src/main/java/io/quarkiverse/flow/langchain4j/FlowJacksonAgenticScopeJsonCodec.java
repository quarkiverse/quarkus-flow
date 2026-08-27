package io.quarkiverse.flow.langchain4j;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScopeJsonCodec;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.AgentMessage;
import dev.langchain4j.agentic.scope.DefaultAgenticScope.Kind;
import dev.langchain4j.agentic.scope.UnserializableAgenticScopeException;
import dev.langchain4j.data.message.ChatMessage;

/**
 * Quarkus Flow implementation of {@link AgenticScopeJsonCodec} that merges ObjectMapper
 * configurations from quarkus-langchain4j and langchain4j's JacksonAgenticScopeJsonCodec.
 *
 * <p>
 * <strong>Configuration Merge Strategy:</strong>
 * <ul>
 * <li><strong>Base:</strong> Uses quarkus-langchain4j's ObjectMapper (has Quarkus Arc integration,
 * can see application classes)</li>
 * <li><strong>Additions:</strong> Copies AgenticScope mixins and polymorphic typing from
 * langchain4j's JacksonAgenticScopeJsonCodec (package-private, so we duplicate the configuration)</li>
 * </ul>
 *
 * <p>
 * <strong>Why copying instead of reusing?</strong> JacksonAgenticScopeJsonCodec and its
 * configuration methods are package-private (internal API). We copy the mixin definitions
 * and polymorphic typing configuration. This is a temporary solution until langchain4j exposes
 * a public API for AgenticScope ObjectMapper configuration.
 *
 * <p>
 * <strong>TODO:</strong> Propose to langchain4j team:
 * <ul>
 * <li>Make {@code agenticScopeJsonMapperBuilder()} public (like
 * {@code JacksonChatMessageJsonCodec.chatMessageJsonMapperBuilder()})</li>
 * <li>Or provide a way to contribute/override ObjectMapper configuration</li>
 * </ul>
 *
 * <p>
 * <strong>Problem this solves:</strong> When using {@code quarkus-flow-jpa} to persist
 * workflow instances, workflows that store application domain objects in the AgenticScope
 * would fail with a classloader-related {@code UnserializableAgenticScopeException}.
 * LangChain4j's default {@code JacksonAgenticScopeJsonCodec} uses a static ObjectMapper
 * in the parent classloader, which cannot see application classes. This codec fixes that
 * by using quarkus-langchain4j's ObjectMapper with the correct classloader context.
 *
 * @see io.quarkiverse.langchain4j.QuarkusJsonCodecFactory
 * @see dev.langchain4j.agentic.scope.JacksonAgenticScopeJsonCodec
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/901">Issue #901</a>
 */
public class FlowJacksonAgenticScopeJsonCodec implements AgenticScopeJsonCodec {

    /**
     * Polymorphic type validator that allows standard Java types and application classes.
     * Langchain4j's JacksonAgenticScopeJsonCodec uses ConfigurablePolymorphicTypeValidator
     * (package-private), so we use Jackson's BasicPolymorphicTypeValidator with equivalent
     * permissive settings.
     */
    private static final PolymorphicTypeValidator TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType(Object.class) // Allow all types (same behavior as ConfigurablePolymorphicTypeValidator)
            .build();

    /**
     * ObjectMapper configured for AgenticScope serialization.
     * Merges quarkus-langchain4j's ObjectMapper with langchain4j's AgenticScope configuration.
     */
    private static final ObjectMapper MAPPER = createAgenticScopeMapper();

    /**
     * Creates an ObjectMapper by merging configurations from two sources:
     *
     * <p>
     * <strong>Base (from quarkus-langchain4j):</strong>
     * <ul>
     * <li>Quarkus Arc container integration (sees application classes via TCCL)</li>
     * <li>Field visibility: FIELD=ANY, ALL=NONE</li>
     * <li>ALLOW_UNESCAPED_CONTROL_CHARS enabled</li>
     * <li>Chat message mixins (ChatMessage, AiMessage, UserMessage, SystemMessage, etc.)</li>
     * <li>QuarkusLangChain4jModule (custom LocalDate/LocalDateTime/LocalTime deserializers)</li>
     * </ul>
     *
     * <p>
     * <strong>Added (copied from JacksonAgenticScopeJsonCodec):</strong>
     * <ul>
     * <li>AgenticScope mixins (DefaultAgenticScope, AgentMessage, AgentInvocation)</li>
     * <li>Polymorphic type handling via activateDefaultTyping (for AgenticScope state)</li>
     * </ul>
     *
     * <p>
     * This merge ensures we get both Quarkus integration AND AgenticScope serialization support.
     */
    private static ObjectMapper createAgenticScopeMapper() {
        // BASE: Start with quarkus-langchain4j's ObjectMapper (has Quarkus integration + chat message mixins)
        ObjectMapper mapper = io.quarkiverse.langchain4j.QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER
                .copy(); // Copy to avoid mutating the shared instance

        // ADD: AgenticScope-specific mixins (copied from JacksonAgenticScopeJsonCodec)
        mapper.addMixIn(DefaultAgenticScope.class, AgenticScopeMixin.class);
        mapper.addMixIn(AgentMessage.class, AgentMessageMixin.class);
        mapper.addMixIn(AgentInvocation.class, AgentInvocationMixin.class);

        // ADD: Polymorphic type handling (copied from JacksonAgenticScopeJsonCodec)
        mapper.activateDefaultTyping(TYPE_VALIDATOR);

        return mapper;
    }

    @Override
    public String toJson(DefaultAgenticScope agenticScope) {
        try {
            // Note: JacksonAgenticScopeJsonCodec calls agenticScope.serializableCopy(),
            // but that method is package-private. Serializing directly works the same way.
            return MAPPER.writeValueAsString(agenticScope);
        } catch (JsonProcessingException e) {
            throw new UnserializableAgenticScopeException(
                    "Failed to serialize AgenticScope to JSON", e);
        }
    }

    @Override
    public DefaultAgenticScope fromJson(String json) {
        try {
            return MAPPER.readValue(json, DefaultAgenticScope.class);
        } catch (InvalidTypeIdException e) {
            throw new UnserializableAgenticScopeException(e.getTypeId(), e);
        } catch (JsonProcessingException e) {
            throw new UnserializableAgenticScopeException(
                    "Failed to deserialize AgenticScope from JSON. " +
                            "If you are storing custom domain objects in the AgenticScope, " +
                            "ensure they are serializable by Jackson.",
                    e);
        }
    }

    // ==================================================================================
    // Jackson mixins for AgenticScope types
    // COPIED from dev.langchain4j.agentic.scope.JacksonAgenticScopeJsonCodec
    // (internal package-private class, so we duplicate the mixin definitions)
    // ==================================================================================

    @JsonInclude(NON_NULL)
    private static abstract class AgenticScopeMixin {
        @JsonCreator
        public AgenticScopeMixin(
                @JsonProperty("memoryId") Object memoryId,
                @JsonProperty("kind") Kind kind) {
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class AgentMessageMixin {
        @JsonCreator
        public AgentMessageMixin(
                @JsonProperty("agentName") String agentName,
                @JsonProperty("agentId") String agentId,
                @JsonProperty("message") ChatMessage message) {
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class AgentInvocationMixin {
        @JsonCreator
        public AgentInvocationMixin(
                @JsonProperty("agentType") Class<?> agentType,
                @JsonProperty("agentName") String agentName,
                @JsonProperty("agentId") String agentId,
                @JsonProperty("input") Map<String, Object> input,
                @JsonProperty("output") Object output) {
        }
    }
}
