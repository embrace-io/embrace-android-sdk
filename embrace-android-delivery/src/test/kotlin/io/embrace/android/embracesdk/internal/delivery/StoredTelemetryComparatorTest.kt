package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.BLOB
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.CRASH
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.LOG
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType.SESSION
import io.embrace.android.embracesdk.internal.worker.PriorityRunnableFuture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.RunnableFuture
import java.util.concurrent.TimeUnit

class StoredTelemetryComparatorTest {

    private val crash = StoredTelemetryMetadata(1, "crash", "pid", CRASH)
    private val session = StoredTelemetryMetadata(1, "session", "pid", SESSION)
    private val session2 = StoredTelemetryMetadata(100, "session2", "pid", SESSION)
    private val session3 = StoredTelemetryMetadata(1000, "session3", "pid", SESSION)
    private val log = StoredTelemetryMetadata(1, "log", "pid", LOG)
    private val network = StoredTelemetryMetadata(1, "network", "pid", BLOB)

    @Test
    fun `sort values`() {
        val result = listOf(network, log, session2, crash, session3, session)
            .map { PriorityRunnableFuture(FakeFuture(), it) }
            .sortedWith(storedTelemetryRunnableComparator)
            .map { it.priorityInfo as StoredTelemetryMetadata }
            .map(StoredTelemetryMetadata::uuid)
        val expected = listOf(crash, session, session2, session3, log, network)
            .map(StoredTelemetryMetadata::uuid)
        assertEquals(expected, result)
    }

    private class FakeFuture : RunnableFuture<StoredTelemetryMetadata> {
        override fun run() {
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean = false

        override fun get(): StoredTelemetryMetadata? = null

        override fun get(
            timeout: Long,
            unit: TimeUnit,
        ): StoredTelemetryMetadata? = null
    }
}
