package io.quarkiverse.flow.dsl.executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class DataTypeConverterRegistry {

    private static final DataTypeConverterRegistry instance = new DataTypeConverterRegistry();

    public static DataTypeConverterRegistry get() {
        return instance;
    }

    @SuppressWarnings("rawtypes")
    private final Iterable<DataTypeConverter> converters;

    @SuppressWarnings("rawtypes")
    private final Map<Class<?>, Optional<DataTypeConverter>> convertersMap;

    private DataTypeConverterRegistry() {
        this.converters = ServiceLoader.load(DataTypeConverter.class);
        this.convertersMap = new ConcurrentHashMap<>();
    }

    @SuppressWarnings("rawtypes")
    public Optional<DataTypeConverter> find(Class<?> clazz) {
        return convertersMap.computeIfAbsent(clazz, this::searchConverter);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Optional<DataTypeConverter> searchConverter(Class<?> clazz) {
        List<DataTypeConverter> candidates = new ArrayList<>();
        for (DataTypeConverter converter : converters) {
            if (converter.sourceType().equals(clazz)) {
                candidates.add(converter);
            }
        }
        if (!candidates.isEmpty()) {
            return first(candidates);
        }

        for (DataTypeConverter converter : converters) {
            if (converter.sourceType().isAssignableFrom(clazz)) {
                candidates.add(converter);
            }
        }
        return candidates.isEmpty() ? Optional.empty() : first(candidates);
    }

    @SuppressWarnings("rawtypes")
    private Optional<DataTypeConverter> first(List<DataTypeConverter> candidates) {
        Collections.sort(candidates);
        return Optional.of(candidates.get(0));
    }
}
