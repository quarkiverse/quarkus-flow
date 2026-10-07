package io.quarkiverse.flow.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowContextPropagatorTest {

    @Test
    @DisplayName("test_no_registered_propagator_is_a_noop")
    void test_no_registered_propagator_is_a_noop() {
        FlowContextPropagator propagator = FlowContextPropagator.Holder.compose(List.of());

        FlowContextPropagator.Snapshot snapshot = propagator.capture();

        assertThat(snapshot).isSameAs(FlowContextPropagator.Snapshot.NONE);
        try (FlowContextPropagator.Scope scope = snapshot.activate()) {
            assertThat(scope).isSameAs(FlowContextPropagator.Scope.NOOP);
        }
    }

    @Test
    @DisplayName("test_single_registered_propagator_is_used_as_is")
    void test_single_registered_propagator_is_used_as_is() {
        FlowContextPropagator only = new RecordingPropagator("only", new ArrayList<>());

        assertThat(FlowContextPropagator.Holder.compose(List.of(only))).isSameAs(only);
    }

    @Test
    @DisplayName("test_composed_propagators_activate_in_order_and_close_in_reverse_order")
    void test_composed_propagators_activate_in_order_and_close_in_reverse_order() {
        List<String> events = new ArrayList<>();
        FlowContextPropagator composed = FlowContextPropagator.Holder.compose(List.of(
                new RecordingPropagator("a", events),
                new RecordingPropagator("b", events)));

        FlowContextPropagator.Snapshot snapshot = composed.capture();
        assertThat(events).containsExactly("capture a", "capture b");

        try (FlowContextPropagator.Scope ignored = snapshot.activate()) {
            assertThat(events).endsWith("activate a", "activate b");
        }

        assertThat(events).endsWith("close b", "close a");
    }

    @Test
    @DisplayName("test_current_without_service_registration_is_a_noop")
    void test_current_without_service_registration_is_a_noop() {
        assertThat(FlowContextPropagator.current().capture()).isSameAs(FlowContextPropagator.Snapshot.NONE);
    }

    private record RecordingPropagator(String name, List<String> events) implements FlowContextPropagator {

        @Override
        public Snapshot capture() {
            events.add("capture " + name);
            return () -> {
                events.add("activate " + name);
                return () -> events.add("close " + name);
            };
        }
    }
}
