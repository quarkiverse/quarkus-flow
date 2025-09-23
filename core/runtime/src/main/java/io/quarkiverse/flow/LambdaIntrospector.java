package io.quarkiverse.flow;

import java.io.Serializable;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LambdaIntrospector {
    // Cache to avoid reflective calls over and over
    private static final Map<Class<?>, MethodRef> CACHE = new ConcurrentHashMap<>();

    private LambdaIntrospector() {
    }

    static MethodRef extract(Serializable lambda) {
        return CACHE.computeIfAbsent(lambda.getClass(), cls -> doExtract(lambda));
    }

    /**
     * @see <a href="https://www.baeldung.com/java-serialize-lambda">Serialize a Lambda in Java</a>
     */
    private static MethodRef doExtract(Serializable lambda) {
        try {
            Method m = lambda.getClass().getDeclaredMethod("writeReplace");
            m.setAccessible(true);
            SerializedLambda sl = (SerializedLambda) m.invoke(lambda);

            String owner = sl.getImplClass().replace('/', '.');
            String name = sl.getImplMethodName();
            String sig = sl.getImplMethodSignature();
            boolean isStatic = sl.getImplMethodKind() == MethodHandleInfo.REF_invokeStatic;

            return new MethodRef(owner, name, sig, isStatic);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot introspect lambda " + lambda, e);
        }
    }

}
