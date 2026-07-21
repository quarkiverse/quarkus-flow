package io.quarkiverse.flow.dsl;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.quarkiverse.flow.dsl.configurers.SwitchCaseConfigurer;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.quarkiverse.flow.dsl.types.SerializablePredicate;
import io.quarkiverse.flow.dsl.types.utils.ReflectionUtils;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;

interface CommonFuncOps {

    default <T, V> Consumer<FuncCallTaskBuilder> fn(Function<T, V> function, Class<T> argClass) {
        return f -> f.function(function, argClass);
    }

    default <T, V> Consumer<FuncCallTaskBuilder> fn(SerializableFunction<T, V> function) {
        Class<T> clazz = ReflectionUtils.inferInputType(function);
        return f -> f.function(function, clazz);
    }

    default Consumer<FuncSwitchTaskBuilder> cases(SwitchCaseConfigurer... cases) {
        return s -> {
            for (SwitchCaseConfigurer c : cases) {
                s.onPredicate(c);
            }
        };
    }

    default <T> SwitchCaseSpec<T> caseOf(Predicate<T> when, Class<T> whenClass) {
        return new SwitchCaseSpec<T>().when(when, whenClass);
    }

    default <T> SwitchCaseSpec<T> caseOf(SerializablePredicate<T> when) {
        return new SwitchCaseSpec<T>().when(when, ReflectionUtils.inferInputType(when));
    }

    default SwitchCaseConfigurer caseDefault(String task) {
        return s -> s.then(task);
    }

    default SwitchCaseConfigurer caseDefault(FlowDirectiveEnum directive) {
        return s -> s.then(directive);
    }
}
