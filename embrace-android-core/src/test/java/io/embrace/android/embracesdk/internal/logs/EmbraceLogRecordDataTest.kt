package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.payload.toEmbracePayload
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbraceLogRecordDataTest {

    @Test
    fun testCreationFromLogRecordData() {
        val embraceLogRecordData = FakeLogRecordData().toEmbracePayload()
        assertEquals(testLog, embraceLogRecordData)
    }
}
