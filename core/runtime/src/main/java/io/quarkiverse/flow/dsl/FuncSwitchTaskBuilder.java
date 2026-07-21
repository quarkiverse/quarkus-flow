package io.quarkiverse.flow.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.spi.ConditionalTaskBuilder;
import io.quarkiverse.flow.dsl.spi.FuncTaskTransformations;
import io.quarkiverse.flow.dsl.types.utils.TaskPredicate;
import io.serverlessworkflow.api.types.FlowDirective;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.SwitchCase;
import io.serverlessworkflow.api.types.SwitchItem;
import io.serverlessworkflow.api.types.SwitchTask;
import io.serverlessworkflow.fluent.spec.TaskBaseBuilder;
import io.serverlessworkflow.fluent.spec.spi.SwitchTaskFluent;

public class FuncSwitchTaskBuilder extends TaskBaseBuilder<FuncSwitchTaskBuilder>
        implements FuncTaskTransformations<FuncSwitchTaskBuilder>,
        ConditionalTaskBuilder<FuncSwitchTaskBuilder>,
        SwitchTaskFluent<FuncSwitchTaskBuilder> {

    private final SwitchTask switchTask;
    private final List<SwitchItem> switchItems;

    FuncSwitchTaskBuilder() {
        this.switchTask = new SwitchTask();
        this.switchItems = new ArrayList<>();
        super.setTask(switchTask);
    }

    @Override
    protected FuncSwitchTaskBuilder self() {
        return this;
    }

    public FuncSwitchTaskBuilder onPredicate(Consumer<SwitchCasePredicateBuilder> consumer) {
        return this.onPredicate(null, consumer);
    }

    public FuncSwitchTaskBuilder onPredicate(
            String name, Consumer<SwitchCasePredicateBuilder> consumer) {
        final SwitchCasePredicateBuilder switchCase = new SwitchCasePredicateBuilder(defaultItemNameIfBlank(name));
        consumer.accept(switchCase);
        final SwitchCase switchCaseValue = switchCase.build();

        // Handling default cases
        if (TaskPredicate.predicate(switchTask, switchCase.name) == null) {
            Objects.requireNonNull(switchCaseValue.getThen(), "Then is required");
            if (switchCaseValue.getThen().getFlowDirectiveEnum() != null) {
                return this.onDefault(switchCaseValue.getThen().getFlowDirectiveEnum());
            } else {
                return this.onDefault(switchCaseValue.getThen().getString());
            }
        }
        this.switchItems.add(new SwitchItem(switchCase.name, switchCase.build()));
        return this;
    }

    @Override
    public FuncSwitchTaskBuilder on(String name, Consumer<SwitchCaseBuilder> switchCaseConsumer) {
        final SwitchCaseBuilder switchCase = new SwitchCaseBuilder();
        switchCaseConsumer.accept(switchCase);
        this.switchItems.add(new SwitchItem(defaultItemNameIfBlank(name), switchCase.build()));
        return this;
    }

    @Override
    public int switchItemCount() {
        return this.switchItems.size();
    }

    public SwitchTask build() {
        this.switchTask.setSwitch(this.switchItems);
        return switchTask;
    }

    public final class SwitchCasePredicateBuilder {
        private final SwitchCase switchCase;
        private final String name;

        SwitchCasePredicateBuilder(String name) {
            this.name = name;
            this.switchCase = new SwitchCase();
        }

        public <T> SwitchCasePredicateBuilder when(Predicate<T> when) {
            TaskPredicate.withPredicate(FuncSwitchTaskBuilder.this.switchTask, name, when);
            return this;
        }

        public <T> SwitchCasePredicateBuilder when(Predicate<T> when, Class<T> whenClass) {
            TaskPredicate.withPredicate(FuncSwitchTaskBuilder.this.switchTask, name, when, whenClass);
            return this;
        }

        public SwitchCasePredicateBuilder then(String taskName) {
            this.switchCase.setThen(new FlowDirective().withString(taskName));
            return this;
        }

        public SwitchCasePredicateBuilder then(FlowDirectiveEnum then) {
            this.switchCase.setThen(new FlowDirective().withFlowDirectiveEnum(then));
            return this;
        }

        public SwitchCase build() {
            return this.switchCase;
        }
    }
}
