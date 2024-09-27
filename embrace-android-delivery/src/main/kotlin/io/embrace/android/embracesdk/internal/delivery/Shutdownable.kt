package io.embrace.android.embracesdk.internal.delivery

interface Shutdownable {

    /**
     * Gracefully shutdown the service.
     */
    fun shutdown()
}
