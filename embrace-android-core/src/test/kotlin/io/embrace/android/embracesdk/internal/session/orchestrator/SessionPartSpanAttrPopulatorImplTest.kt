package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeLogLimitingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionPartSpanAttrPopulatorImplTest {

    private val zygote = SessionPartToken("id", 1, 5, AppState.FOREGROUND, false, LifeEventType.STATE)
    private lateinit var userSessionPropertiesService: FakeUserSessionPropertiesService
    private lateinit var populator: SessionPartSpanAttrPopulatorImpl
    private lateinit var destination: FakeTelemetryDestination

    @Before
    fun setUp() {
        userSessionPropertiesService = FakeUserSessionPropertiesService()
        destination = FakeTelemetryDestination()
        populator = SessionPartSpanAttrPopulatorImpl(
            destination,
            { 0 },
            FakeLogLimitingService(),
            FakeMetadataService()
        )
    }

    @Test
    fun `start attributes populated`() {
        populator.populateSessionSpanStartAttrs(zygote)

        val attrs = destination.attributes
        val expected = mapOf(
            EmbSessionAttributes.EMB_COLD_START to "false",
            EmbSessionAttributes.EMB_SESSION_NUMBER to "5",
            EmbSessionAttributes.EMB_STATE to "foreground",
            EmbSessionAttributes.EMB_CLEAN_EXIT to "false",
            EmbSessionAttributes.EMB_TERMINATED to "true",
            EmbSessionAttributes.EMB_SESSION_START_TYPE to "state"
        )
        assertEquals(expected, attrs)
    }

    @Test
    fun `end attributes populated`() {
        populator.populateSessionSpanEndAttrs(LifeEventType.STATE, "crashId", false, emptyMap())

        val attrs = destination.attributes
        val expected = mapOf(
            EmbSessionAttributes.EMB_CLEAN_EXIT to "true",
            EmbSessionAttributes.EMB_TERMINATED to "false",
            EmbSessionAttributes.EMB_CRASH_ID to "crashId",
            EmbSessionAttributes.EMB_SESSION_END_TYPE to "state",
            EmbSessionAttributes.EMB_ERROR_LOG_COUNT to "0",
            EmbSessionAttributes.EMB_DISK_FREE_BYTES to "500000000"
        )
        assertEquals(expected, attrs)
    }

    @Test
    fun `end attributes - final session part`() {
        populator.populateSessionSpanEndAttrs(
            LifeEventType.STATE,
            null,
            false,
            mapOf(
                EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART to "1",
                EmbSessionAttributes.EMB_TERMINATION_REASON to EmbSessionAttributes.EmbTerminationReasonValues.MANUAL,
            ),
        )

        val attrs = destination.attributes
        val expected = mapOf(
            EmbSessionAttributes.EMB_CLEAN_EXIT to "true",
            EmbSessionAttributes.EMB_TERMINATED to "false",
            EmbSessionAttributes.EMB_SESSION_END_TYPE to "state",
            EmbSessionAttributes.EMB_ERROR_LOG_COUNT to "0",
            EmbSessionAttributes.EMB_DISK_FREE_BYTES to "500000000",
            EmbSessionAttributes.EMB_IS_FINAL_SESSION_PART to "1",
            EmbSessionAttributes.EMB_TERMINATION_REASON to "manual",
        )
        assertEquals(expected, attrs)
    }
}
