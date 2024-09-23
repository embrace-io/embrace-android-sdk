package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.LOG
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.NETWORK
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredTelemetryComparatorTest {

    private val crash = StoredTelemetryMetadata(1, "crash", CRASH)
    private val session = StoredTelemetryMetadata(1, "session", SESSION)
    private val session2 = StoredTelemetryMetadata(100, "session2", SESSION)
    private val session3 = StoredTelemetryMetadata(1000, "session3", SESSION)
    private val log = StoredTelemetryMetadata(1, "log", LOG)
    private val network = StoredTelemetryMetadata(1, "network", NETWORK)

    @Test
    fun `sort values`() {
        val result = listOf(network, log, session2, crash, session3, session)
            .sortedWith(StoredTelemetryComparator)
            .map(StoredTelemetryMetadata::uuid)
        val expected = listOf(crash, session, session2, session3, log, network)
            .map(StoredTelemetryMetadata::uuid)
        assertEquals(expected, result)
    }
}
