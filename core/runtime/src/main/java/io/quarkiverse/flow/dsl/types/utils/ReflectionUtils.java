package io.quarkiverse.flow.dsl.types.utils;

import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.dsl.types.ContextFunction;
import io.quarkiverse.flow.dsl.types.FilterFunction;
import io.quarkiverse.flow.dsl.types.InstanceIdFunction;
import io.quarkiverse.flow.dsl.types.SerializableConsumer;
import io.quarkiverse.flow.dsl.types.SerializableFunction;
import io.quarkiverse.flow.dsl.types.SerializablePredicate;
import io.quarkiverse.flow.dsl.types.UniqueIdBiFunction;

/**
 * Specially used by {@link Function} parameters in the Java Function.
 *
 * @see <a href="https://www.baeldung.com/java-serialize-lambda">Serialize a Lambda in Java</a>
 */
public final class ReflectionUtils {

    private static final Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);

    private ReflectionUtils() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(ContextFunction<T, ?> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(FilterFunction<T, ?> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(SerializableFunction<T, ?> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(SerializablePredicate<T> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(InstanceIdFunction<T, ?> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 1);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(UniqueIdBiFunction<T, ?> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 1);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferInputType(SerializableConsumer<T> fn) {
        return (Class<T>) inferInputTypeFromAny(fn, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> inferResultType(Object fn) {
        return (Class<T>) inferOutputType(inferMethodType(fn));
    }

    public static Class<?> inferInputTypeFromAny(Object fn, int lambdaParamIndex) {
        return inferInputType(inferMethodType(fn), lambdaParamIndex);
    }

    public static Optional<SerializedLambda> serializedFromFunction(Object fn) {
        try {
            return Optional.of(serializedLambda(fn));
        } catch (ReflectiveOperationException ex) {
            logger.debug("Error resolving serialized lambda for {}", fn, ex);
            return Optional.empty();
        }
    }

    public static Class<?> loadCapturingClass(String capturingClass) throws ClassNotFoundException {
        return loadClass(capturingClass.replace('/', '.'));
    }

    public static Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className, false, getClassLoader());
    }

    private static ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ReflectionUtils.class.getClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
        }
        return cl;
    }

    public static Object functionFromSerialized(SerializedLambda sl)
            throws ReflectiveOperationException {
        Method deserializeMethod = loadCapturingClass(sl.getCapturingClass())
                .getDeclaredMethod("$deserializeLambda$", SerializedLambda.class);
        deserializeMethod.setAccessible(true);
        return deserializeMethod.invoke(null, sl);
    }

    public static Optional<MethodType> methodType(Object fn) {
        return serializedFromFunction(fn).map(ReflectionUtils::inferMethodType);
    }

    public static MethodType inferMethodType(SerializedLambda sl) {
        // getInstantiatedMethodType() provides the exact generic signature resolved
        // by the compiler, completely bypassing captured variables and method kind switches!
        return MethodType.fromMethodDescriptorString(sl.getInstantiatedMethodType(), getClassLoader());
    }

    public static SerializedLambda serializedLambda(Object fn)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method m = fn.getClass().getDeclaredMethod("writeReplace");
        m.setAccessible(true);
        return (SerializedLambda) m.invoke(fn);
    }

    private static Class<?> inferInputType(MethodType type, int index) {
        return type.parameterType(index);
    }

    private static Class<?> inferOutputType(MethodType type) {
        return type.returnType();
    }

    private static MethodType inferMethodType(Object fn) {
        try {
            SerializedLambda sl = serializedLambda(fn);
            return inferMethodType(sl);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Cannot infer type from lambda. Pass Class<T> or use a method reference.", ex);
        }
    }
}
