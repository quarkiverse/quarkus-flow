package io.quarkiverse.flow.dsl.executors;

public class StringBuilder2String implements DataTypeConverter<StringBuilder, String> {

    @Override
    public String apply(StringBuilder t) {
        return t.toString();
    }

    @Override
    public Class<StringBuilder> sourceType() {
        return StringBuilder.class;
    }

    @Override
    public Class<String> targetType() {
        return String.class;
    }
}
