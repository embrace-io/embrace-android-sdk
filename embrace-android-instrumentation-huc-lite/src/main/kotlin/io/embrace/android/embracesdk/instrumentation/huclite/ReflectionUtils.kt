package io.embrace.android.embracesdk.instrumentation.huclite

import java.lang.reflect.Method

@Throws(NoSuchMethodException::class)
internal fun findDeclaredMethod(
    obj: Any,
    objClz: Class<*>,
    methodName: String,
    vararg methodParams: Class<*>,
): Method {
    try {
        val method = objClz.getDeclaredMethod(methodName, *methodParams)
        method.isAccessible = true
        return method
    } catch (ignored: NoSuchMethodException) {
        val superClz = objClz.superclass ?: throw ignored
        return findDeclaredMethod(obj, superClz, methodName, *methodParams)
    }
}
