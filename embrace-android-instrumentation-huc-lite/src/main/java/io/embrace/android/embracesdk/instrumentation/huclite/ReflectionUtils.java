package io.embrace.android.embracesdk.instrumentation.huclite;

import java.lang.reflect.Method;

class ReflectionUtils {
    /*
     * This class is written in Java to avoid the Kotlin compiler adding an implicit `Arrays.copyOf`
     * when using the spread operator.
     */
    private ReflectionUtils() {}

    /**
     * Search for a given declared method up the object tree.
     *
     * @param declaringClass the base class to look for the method on
     * @param methodName the name of the method to find
     * @param methodArguments the method argument types
     * @return the discovered method
     */
    static Method findDeclaredMethod(
        Class<?> declaringClass,
        String methodName,
        Class<?>[] methodArguments
    ) throws NoSuchMethodException {
        try {
            Method m = declaringClass.getDeclaredMethod(methodName, methodArguments);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
            return findDeclaredMethod(declaringClass.getSuperclass(), methodName, methodArguments);
        }
    }
}
