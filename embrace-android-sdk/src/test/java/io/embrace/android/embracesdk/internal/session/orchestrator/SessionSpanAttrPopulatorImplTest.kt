package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionSpanAttrPopulatorImplTest {

    private val zygote = SessionZygote("id", 1, 5, ApplicationState.FOREGROUND, false, LifeEventType.STATE)
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var populator: SessionSpanAttrPopulatorImpl
    private lateinit var writer: FakeCurrentSessionSpan

    @Before
    fun setUp() {
        sessionPropertiesService = FakeSessionPropertiesService()
        writer = FakeCurrentSessionSpan()
        populator = SessionSpanAttrPopulatorImpl(
            writer,
            FakeEventService(),
            FakeStartupService(),
            FakeLogService(),
            FakeMetadataService(),
            sessionPropertiesService
        )
    }

    @Test
    fun `start attributes populated`() {
        populator.populateSessionSpanStartAttrs(zygote)

        val attrs = writer.attributes
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

        val attrs = writer.attributes
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
}
