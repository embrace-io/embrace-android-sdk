package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator
import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.LifeEventType
import io.embrace.android.embracesdk.payload.SessionZygote
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionSpanAttrPopulatorTest {

    private val zygote = SessionZygote("id", 1, 5, ApplicationState.FOREGROUND, false, LifeEventType.STATE)
    private lateinit var populator: SessionSpanAttrPopulator
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        writer = FakeCurrentSessionSpan()
        populator = SessionSpanAttrPopulator(
            writer,
            FakeEventService(),
            FakeStartupService(),
            FakeLogService(),
            FakeMetadataService()
        )
    }

    @Test
    fun `start attributes populated`() {
        populator.populateSessionSpanStartAttrs(zygote)

        val attrs = getSpanAttrs()
        val expected = mapOf(
            "emb.cold_start" to "false",
            "emb.session_number" to "5",
            "emb.state" to "foreground",
            "emb.clean_exit" to "false",
            "emb.terminated" to "true",
            "emb.session_start_type" to "state"
        )
        assertEquals(expected, attrs)
    }

    @Test
    fun `end attributes populated`() {
        populator.populateSessionSpanEndAttrs(LifeEventType.STATE, "crashId", false)

        val attrs = getSpanAttrs()
        val expected = mapOf(
            "emb.clean_exit" to "true",
            "emb.terminated" to "false",
            "emb.crash_id" to "crashId",
            "emb.session_end_type" to "state",
            "emb.error_log_count" to "0",
            "emb.disk_free_bytes" to "500000000"
        )
        assertEquals(expected, attrs)
    }

    private fun getSpanAttrs() = writer.addedAttributes.associateBy { it.key }.mapValues { it.value.value }
}
