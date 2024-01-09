package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.internal.spans.SpansService

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace.
 */
internal interface DataSource<T> {

    /**
     * Service where captured data will be sent.
     */
    val spansService: SpansService

    /**
     * Register any listeners that are required for capturing data.
     */
    fun registerListeners()

    /**
     * Unregister any listeners that might be capturing data.
     */
    fun unregisterListeners()
}
