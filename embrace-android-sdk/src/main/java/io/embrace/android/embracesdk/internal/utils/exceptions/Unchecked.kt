package io.embrace.android.embracesdk.internal.utils.exceptions

import java.lang.reflect.InvocationTargetException

internal object Unchecked {
    /**
     * Propagates `throwable` as-is if possible, or by wrapping in a `RuntimeException` if not.
     *
     *  * If `throwable` is an `InvocationTargetException` the cause is extracted and processed recursively.
     *  * If `throwable` is an `InterruptedException` then the thread is interrupted and a `RuntimeException` is thrown.
     *  * If `throwable` is an `Error` or `RuntimeException` it is propagated as-is.
     *  * Otherwise `throwable` is wrapped in a `RuntimeException` and thrown.
     *
     * This method always throws an exception. The return type is a convenience to satisfy the type system
     * when the enclosing method returns a value. For example:
     * ```
     * T foo() {
     * try {
     * return methodWithCheckedException();
     * } catch (Exception e) {
     * throw Unchecked.propagate(e);
     * }
     * }
     * ```
     *
     * @param throwable the `Throwable` to propagate
     * @return nothing; this method always throws an exception
     */
    @JvmStatic
    fun propagate(throwable: Throwable?): RuntimeException {
        if (throwable is InvocationTargetException) {
            throw propagate(throwable.cause)
        } else {
            if (throwable is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            @Suppress("TooGenericExceptionThrown") // maintain backwards compat.
            throw RuntimeException(throwable)
        }
    }
}
