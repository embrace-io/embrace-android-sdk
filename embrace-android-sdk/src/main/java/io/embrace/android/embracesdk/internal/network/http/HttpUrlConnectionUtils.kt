package io.embrace.android.embracesdk.internal.network.http

import java.lang.reflect.Method

/**
 * Searches the object for a declared method, and continues up the hierarchy until it finds a declaration.
 * Otherwise this behaves the same as Class#getDeclaredMethod().
 */

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
