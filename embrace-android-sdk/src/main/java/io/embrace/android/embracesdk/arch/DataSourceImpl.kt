package io.embrace.android.embracesdk.arch

/**
 * An abstract implementation of [DataSource] that captures data and writes it to a [DataSink].
 * This base class contains convenience functions for capturing data that makes the syntax nicer
 * in subclasses.
 */
internal abstract class DataSourceImpl(
    private val sink: DataSinkProvider
) : DataSource {

    override fun captureData(action: DataSinkMutator) {
        action(sink())
    }
}
