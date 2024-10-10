package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.LOG
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.NETWORK
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.worker.PriorityRunnableFuture
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredTelemetryComparatorTest {

    private val crash = StoredTelemetryMetadata(1, "crash", "pid", CRASH)
    private val session = StoredTelemetryMetadata(1, "session", "pid", SESSION)
    private val session2 = StoredTelemetryMetadata(100, "session2", "pid", SESSION)
    private val session3 = StoredTelemetryMetadata(1000, "session3", "pid", SESSION)
    private val log = StoredTelemetryMetadata(1, "log", "pid", LOG)
    private val network = StoredTelemetryMetadata(1, "network", "pid", NETWORK)

    @Test
    fun `sort values`() {
        val result = listOf(network, log, session2, crash, session3, session)
            .map { PriorityRunnableFuture<StoredTelemetryMetadata>(mockk(relaxed = true), it) }
            .sortedWith(storedTelemetryRunnableComparator)
            .map { it.priorityInfo as StoredTelemetryMetadata }
            .map(StoredTelemetryMetadata::uuid)
        val expected = listOf(crash, session, session2, session3, log, network)
            .map(StoredTelemetryMetadata::uuid)
        assertEquals(expected, result)
    }
}
