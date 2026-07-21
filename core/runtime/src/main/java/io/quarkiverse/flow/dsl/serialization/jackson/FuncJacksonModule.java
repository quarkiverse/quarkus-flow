package io.quarkiverse.flow.dsl.serialization.jackson;

import java.lang.invoke.SerializedLambda;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.LoopFunction;
import io.quarkiverse.flow.dsl.types.LoopFunctionIndex;
import io.quarkiverse.flow.dsl.types.LoopPredicate;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndex;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndexContext;
import io.quarkiverse.flow.dsl.types.LoopPredicateIndexFilter;
import io.quarkiverse.flow.dsl.types.SerializableConsumer;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.quarkiverse.flow.dsl.types.SerializablePredicate;
import io.serverlessworkflow.api.types.ExportAs;
import io.serverlessworkflow.api.types.FunctionArguments;
import io.serverlessworkflow.api.types.InputFrom;
import io.serverlessworkflow.api.types.OutputAs;
import io.serverlessworkflow.api.types.TaskMetadata;

@SuppressWarnings("unchecked")
public class FuncJacksonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public void setupModule(com.fasterxml.jackson.databind.Module.SetupContext context) {
        SerializableFunctionSerializer serializer = new SerializableFunctionSerializer();
        super.addSerializer(SerializableFunction.class, serializer);
        super.addSerializer(SerializablePredicate.class, serializer);
        super.addSerializer(SerializableConsumer.class, serializer);
        super.addSerializer(ContextFunction.class, serializer);
        super.addSerializer(FilterFunction.class, serializer);
        super.addSerializer(LoopFunction.class, serializer);
        super.addSerializer(LoopFunctionIndex.class, serializer);
        super.addSerializer(LoopPredicate.class, serializer);
        super.addSerializer(LoopPredicateIndex.class, serializer);
        super.addSerializer(LoopPredicateIndexContext.class, serializer);
        super.addSerializer(LoopPredicateIndexFilter.class, serializer);

        super.addSerializer(TaskMetadata.class, new TaskMetadataSerializer());
        super.addDeserializer(TaskMetadata.class, new TaskMetadataDeserializer());
        super.addSerializer(FunctionArguments.class, new FunctionArgumentsSerializer());
        super.addDeserializer(FunctionArguments.class, new FunctionArgumentsDeserializer());
        super.addDeserializer(Function.class, new FunctionDeserializer(Function.class));
        super.addDeserializer(Predicate.class, new FunctionDeserializer(Predicate.class));
        super.addDeserializer(Consumer.class, new FunctionDeserializer(Consumer.class));
        super.addDeserializer(ContextFunction.class, new FunctionDeserializer(ContextFunction.class));
        super.addDeserializer(FilterFunction.class, new FunctionDeserializer(FilterFunction.class));
        super.addDeserializer(LoopFunction.class, new FunctionDeserializer(LoopFunction.class));
        super.addDeserializer(
                LoopFunctionIndex.class, new FunctionDeserializer(LoopFunctionIndex.class));
        super.addDeserializer(LoopPredicate.class, new FunctionDeserializer(LoopPredicate.class));
        super.addDeserializer(
                LoopPredicateIndex.class, new FunctionDeserializer(LoopPredicateIndex.class));
        super.addDeserializer(
                LoopPredicateIndexContext.class, new FunctionDeserializer(LoopPredicateIndexContext.class));
        super.addDeserializer(
                LoopPredicateIndexFilter.class, new FunctionDeserializer(LoopPredicateIndexFilter.class));

        super.setSerializerModifier(
                new BeanSerializerModifier() {
                    @Override
                    public List<BeanPropertyWriter> changeProperties(
                            SerializationConfig config,
                            BeanDescription beanDesc,
                            List<BeanPropertyWriter> beanProperties) {
                        if (beanDesc.getBeanClass().equals(SerializedLambda.class)) {
                            beanProperties.add(new SerializedLambdaWriter(beanProperties.get(0)));
                        }
                        return beanProperties;
                    }
                });
        super.addDeserializer(SerializedLambda.class, new SerializedLambdaDeserializer());

        super.setMixInAnnotation(OutputAs.class, FuncOutputAsMixIn.class);
        super.setMixInAnnotation(ExportAs.class, FuncExportAsMixIn.class);
        super.setMixInAnnotation(InputFrom.class, FuncInputFromMixIn.class);

        super.setupModule(context);
    }
}
