package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceLogRecordDataTest {

    private val serializer = EmbraceSerializer()

    @Test
    fun testDeserialization() {
        val deserializedLog = deserializeJsonFromResource<EmbraceLogRecordData>("log_expected.json")
        assertEquals(testLog.traceId, deserializedLog.traceId)
        assertEquals(testLog.spanId, deserializedLog.spanId)
        assertEquals(testLog.timeUnixNanos, deserializedLog.timeUnixNanos)
        assertEquals(testLog.severityNumber, deserializedLog.severityNumber)
        assertEquals(testLog.severityText, deserializedLog.severityText)
        assertEquals(testLog.body, deserializedLog.body)
        assertEquals(testLog.attributes, deserializedLog.attributes)
    }

    @Test
    fun testSerialization() {
        // Take a log, serialize it to JSON, then deserialize it back and compare. This avoids having to deal with the exactly
        // serialized form details like whitespace that won't matter when we deserialize it back to the object form.
        val serializedLogJson = serializer.toJson(testLog)
        val deserializedLog = serializer.fromJson(serializedLogJson, EmbraceLogRecordData::class.java)
        assertEquals(testLog, deserializedLog)
    }

    @Test
    fun testCreationFromLogRecordData() {
        val embraceLogRecordData = EmbraceLogRecordData(FakeLogRecordData())
        assertEquals(testLog, embraceLogRecordData)
    }
}
