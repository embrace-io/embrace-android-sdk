package io.embrace.android.embracesdk.instrumentation.huclite

import java.io.IOException
import java.lang.reflect.Method
import kotlin.reflect.KProperty

/**
 * Lazy method lookup which resolves once and then either returns the target `Method`
 * or thrown an `IOException` to indicate that the method could not be found.
 */
internal class LazyMethodLookup(
    parentClass: Class<*>,
    methodName: String,
    args: Array<Class<*>>,
) {
    private var state: Any = MethodLookup(parentClass, methodName, args)

    @Throws(IOException::class)
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Method {
        when (val s = state) {
            is Method -> return s
            is MethodLookup -> {
                try {
                    val method = s.resolve()
                    state = method
                } catch (_: NoSuchMethodException) {
                    val error = IOException("Failed to instrument ${s.parentClass.simpleName}")
                    state = error
                }
            }
            is Throwable -> throw s
            else -> throw IOException("Unknown state $state")
        }

        return getValue(thisRef, property)
    }

    class MethodLookup(
        val parentClass: Class<*>,
        val methodName: String,
        val args: Array<Class<*>>,
    ) {
        @Throws(NoSuchMethodException::class)
        fun resolve(): Method {
            return ReflectionUtils.findDeclaredMethod(parentClass, methodName, args)
        }
    }
}
