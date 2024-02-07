package io.embrace.android.embracesdk.arch

/**
 * A function that acts on a [DataSink] and mutates its state.
 */
internal typealias DataSinkMutator = DataSink.() -> Unit

/**
 * Defines a 'data source'. This should be responsible for capturing a specific type
 * of data that will be sent to Embrace.
 */
internal interface DataSource {

    /**
     * All captured data should be written to the data sink within the action of this function.
     */
    fun captureData(action: DataSinkMutator)

    /**
     * Register any listeners that are required for capturing data.
     */
    fun registerListeners()

    /**
     * Unregister any listeners that might be capturing data.
     */
    fun unregisterListeners()
}
