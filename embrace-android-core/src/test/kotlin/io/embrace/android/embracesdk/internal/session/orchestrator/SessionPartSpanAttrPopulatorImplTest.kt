package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.fakes.FakeLogLimitingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues
import io.opentelemetry.kotlin.semconv.SessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

internal class SessionPartSpanAttrPopulatorImplTest {

    private val zygote = SessionPartToken(
        sessionPartId = "id",
        userSessionId = "fake-user-session-id",
        startTime = 1,
        appState = AppState.FOREGROUND,
        isColdStart = false,
        startType = LifeEventType.STATE,
        sessionPartNumber = 5
    )
    private val userSession = UserSessionMetadata(
        startTimeMs = 1000L,
        userSessionId = "user-session-uuid",
        userSessionNumber = 3L,
        maxDurationSecs = 43200L,
        inactivityTimeoutSecs = 1800L,
        partNumber = 2,
        lastActivityMs = 1000L,
    )
    private lateinit var populator: SessionPartSpanAttrPopulatorImpl
    private lateinit var destination: FakeTelemetryDestination

    @Before
    fun setUp() {
        destination = FakeTelemetryDestination()
        populator = SessionPartSpanAttrPopulatorImpl(
            destination,
            { 0 },
            FakeLogLimitingService(),
            FakeMetadataService()
        )
    }

    @Test
    fun `start attributes populated with user session`() {
        populator.populateSessionSpanStartAttrs(zygote, userSession)

        val attrs = destination.attributes
        assertEquals("false", attrs[EmbSessionAttributes.EMB_COLD_START])
        assertEquals("5", attrs[EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER])
        assertEquals("foreground", attrs[EmbSessionAttributes.EMB_STATE])
        assertEquals("false", attrs[EmbSessionAttributes.EMB_CLEAN_EXIT])
        assertEquals("true", attrs[EmbSessionAttributes.EMB_TERMINATED])
        assertEquals("state", attrs[EmbSessionAttributes.EMB_SESSION_START_TYPE])
        assertEquals("1000", attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS])
        assertEquals("user-session-uuid", attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals("3", attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER])
        assertEquals("43200", attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS])
        assertEquals("1800", attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS])
        assertEquals("user-session-uuid", attrs[SessionAttributes.SESSION_ID])
        assertEquals("id", attrs[EmbSessionAttributes.EMB_SESSION_PART_ID])
    }

    @Test
    fun `start attributes populated without user session`() {
        populator.populateSessionSpanStartAttrs(zygote, userSession = null)

        val attrs = destination.attributes
        assertEquals("false", attrs[EmbSessionAttributes.EMB_COLD_START])
        assertEquals("foreground", attrs[EmbSessionAttributes.EMB_STATE])
        assertEquals("false", attrs[EmbSessionAttributes.EMB_CLEAN_EXIT])
        assertEquals("true", attrs[EmbSessionAttributes.EMB_TERMINATED])
        assertEquals("state", attrs[EmbSessionAttributes.EMB_SESSION_START_TYPE])
        assertEquals("", attrs[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertEquals("", attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals("", attrs[SessionAttributes.SESSION_ID])

        assertFalse(attrs.containsKey(EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER))
        assertFalse(attrs.containsKey(EmbSessionAttributes.EMB_USER_SESSION_NUMBER))
        assertFalse(attrs.containsKey(EmbSessionAttributes.EMB_USER_SESSION_START_TS))
        assertFalse(attrs.containsKey(EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS))
        assertFalse(attrs.containsKey(EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS))
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
                EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON to EmbUserSessionTerminationReasonValues.MANUAL,
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
            EmbSessionAttributes.EMB_USER_SESSION_TERMINATION_REASON to "manual",
        )
        assertEquals(expected, attrs)
    }
}
