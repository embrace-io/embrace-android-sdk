package io.embrace.android.embracesdk.instrumentation.huc

interface CheckedSupplier<R> {

    /**
     * Gets a result.
     *
     * @return a result
     * @throws Throwable if an error occurs
     */
    @Throws(Throwable::class)
    fun get(): R
}
