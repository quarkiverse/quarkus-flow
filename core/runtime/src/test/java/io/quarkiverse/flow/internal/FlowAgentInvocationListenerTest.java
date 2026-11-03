package io.quarkiverse.flow.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowAgentInvocationListenerTest {

    @Test
    @DisplayName("test_no_registered_listener_is_a_noop")
    void test_no_registered_listener_is_a_noop() {
        FlowAgentInvocationListener listener = FlowAgentInvocationListener.Holder.compose(List.of());

        assertThatCode(() -> listener.onAgentInvoked("triageAgent")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test_single_registered_listener_is_used_as_is")
    void test_single_registered_listener_is_used_as_is() {
        FlowAgentInvocationListener only = new RecordingListener("only", new ArrayList<>());

        assertThat(FlowAgentInvocationListener.Holder.compose(List.of(only))).isSameAs(only);
    }

    @Test
    @DisplayName("test_composed_listeners_are_all_notified")
    void test_composed_listeners_are_all_notified() {
        List<String> events = new ArrayList<>();
        FlowAgentInvocationListener composed = FlowAgentInvocationListener.Holder.compose(List.of(
                new RecordingListener("a", events),
                new RecordingListener("b", events)));

        composed.onAgentInvoked("triageAgent");

        assertThat(events).containsExactly("a:triageAgent", "b:triageAgent");
    }

    @Test
    @DisplayName("test_current_without_service_registration_is_a_noop")
    void test_current_without_service_registration_is_a_noop() {
        assertThatCode(() -> FlowAgentInvocationListener.current().onAgentInvoked("triageAgent"))
                .doesNotThrowAnyException();
    }

    private record RecordingListener(String name, List<String> events) implements FlowAgentInvocationListener {

        @Override
        public void onAgentInvoked(String agentName) {
            events.add(name + ":" + agentName);
        }
    }
}
