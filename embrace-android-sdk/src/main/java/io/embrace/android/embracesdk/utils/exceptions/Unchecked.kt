package io.embrace.android.embracesdk.utils.exceptions

import io.embrace.android.embracesdk.utils.exceptions.function.CheckedSupplier
import java.lang.reflect.InvocationTargetException

/**
 * Static utility methods that convert checked exceptions to unchecked.
 *
 * Two `wrap()` methods are provided that can wrap an arbitrary piece of logic
 * and convert checked exceptions to unchecked.
 *
 * A number of other methods are provided that allow a lambda block to be decorated
 * to avoid handling checked exceptions.
 * For example, the method [java.io.File.getCanonicalFile] throws an [java.io.IOException]
 * which can be handled as follows:
 * ```
 * stream.map(Unchecked.function(file -&gt; file.getCanonicalFile())
 * ```
 *
 * Each method accepts a functional interface that is defined to throw [Throwable].
 * Catching `Throwable` means that any method can be wrapped.
 * Any `InvocationTargetException` is extracted and processed recursively.
 * Any [Error] or [RuntimeException] is re-thrown without alteration.
 * Any other exception is wrapped in a [RuntimeException].
 */
internal object Unchecked {

    /**
     * Wraps a block of code, converting checked exceptions to unchecked.
     * ```
     * Unchecked.wrap(() -&gt; {
     * // any code that throws a checked exception
     * }
     * ```
     *
     * If a checked exception is thrown it is converted to a [RuntimeException].
     *
     * @param <T>   the type of the result
     * @param block the code block to wrap
     * @return the result of invoking the block
     * @throws RuntimeException if an exception occurs
     </T> */
    @JvmStatic
    fun <T> wrap(block: CheckedSupplier<T>): T {
        return try {
            block.get()
        } catch (ex: Throwable) {
            throw propagate(ex)
        }
    }

    fun <T> wrap(block: () -> T): T = wrap(object : CheckedSupplier<T> {
        override fun get(): T = block()
    })

    /**
     * Propagates `throwable` as-is if possible, or by wrapping in a `RuntimeException` if not.
     *
     *  * If `throwable` is an `InvocationTargetException` the cause is extracted and processed recursively.
     *  * If `throwable` is an `InterruptedException` then the thread is interrupted
     * and a `RuntimeException` is thrown.
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
