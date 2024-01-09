package io.embrace.android.embracesdk.arch

/**
 * Contains all the data sinks that are captured during a session. This is used to create
 * a payload that is sent to the server.
 */
internal class SinkState(
//    val exampleDataSink: DataSink<ExampleData> = DataSink()
) {

    fun toPayload() = Payload(
//        data = captureDataSafely(exampleDataSink::getCapturedData)
    )
}
