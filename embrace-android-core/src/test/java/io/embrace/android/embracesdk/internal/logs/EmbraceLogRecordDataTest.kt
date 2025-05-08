package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.otel.payload.MAX_PROPERTY_SIZE
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.fromMap
import io.opentelemetry.api.common.Attributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EmbraceLogRecordDataTest {

    @Test
    fun testCreationFromLogRecordData() {
        val embraceLogRecordData = FakeLogRecordData().toEmbracePayload()
        assertEquals(testLog, embraceLogRecordData)
    }

    @Test
    fun testPropertyLimitExceeded() {
        val inputMap = (0..20).associateBy { "$it" }.mapValues { it.toString() }
        val inputAttrs = Attributes.builder().fromMap(inputMap, false).build()
        val actual = inputAttrs.toEmbracePayload()
        assertTrue("Unexpected normalized map size.", actual.size <= MAX_PROPERTY_SIZE)
    }
}
