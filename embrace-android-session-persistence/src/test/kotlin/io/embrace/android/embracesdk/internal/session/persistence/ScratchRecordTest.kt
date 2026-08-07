package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Test

internal class ScratchRecordTest {

    @Test
    fun `scratch record round-trips through protobuf encoding`() {
        val record = ScratchRecord(id = "session-123", timestamp_ms = 1_700_000_000_000L)

        val decoded = ScratchRecord.ADAPTER.decode(ScratchRecord.ADAPTER.encode(record))

        assertEquals(record, decoded)
    }
}
