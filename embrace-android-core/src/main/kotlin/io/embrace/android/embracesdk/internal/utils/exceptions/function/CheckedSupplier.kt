package io.embrace.android.embracesdk.internal.utils.exceptions.function

public interface CheckedSupplier<R> {

    /**
     * Gets a result.
     *
     * @return a result
     * @throws Throwable if an error occurs
     */
    @Throws(Throwable::class)
    public fun get(): R
}
