package io.embrace.android.embracesdk.arch

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace.
 */
internal interface DataSource {

    /**
     * Register any listeners that are required for capturing data.
     */
    fun registerListeners()

    /**
     * Unregister any listeners that might be capturing data.
     */
    fun unregisterListeners()
}
