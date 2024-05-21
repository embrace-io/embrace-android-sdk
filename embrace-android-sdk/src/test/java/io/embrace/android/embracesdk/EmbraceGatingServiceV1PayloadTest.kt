package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.LocalConfigParser
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_CUSTOM
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_CUSTOM_VIEWS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_TAPS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_VIEWS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.BREADCRUMBS_WEB_VIEWS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.FULL_SESSION_CRASHES
import io.embrace.android.embracesdk.gating.SessionGatingKeys.FULL_SESSION_ERROR_LOGS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOGS_INFO
import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOGS_WARN
import io.embrace.android.embracesdk.gating.SessionGatingKeys.LOG_PROPERTIES
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_ANR
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CONNECTIVITY
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CPU
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_NETWORK
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_MOMENTS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_ORIENTATIONS
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_PROPERTIES
import io.embrace.android.embracesdk.gating.SessionGatingKeys.SESSION_USER_TERMINATION
import io.embrace.android.embracesdk.gating.SessionGatingKeys.STARTUP_MOMENT
import io.embrace.android.embracesdk.gating.SessionGatingKeys.USER_PERSONAS
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.Event
import io.embrace.android.embracesdk.payload.EventMessage
import io.embrace.android.embracesdk.payload.Orientation
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.utils.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceGatingServiceV1PayloadTest {

    private lateinit var localConfig: LocalConfig
    private lateinit var gatingService: EmbraceGatingService
    private lateinit var configService: ConfigService
    private lateinit var logger: EmbLogger

    private val enabledComponentsFull = setOf(
        BREADCRUMBS_TAPS,
        BREADCRUMBS_WEB_VIEWS,
        BREADCRUMBS_CUSTOM,
        BREADCRUMBS_VIEWS,
        BREADCRUMBS_CUSTOM_VIEWS,
        SESSION_PROPERTIES,
        SESSION_USER_TERMINATION,
        SESSION_ORIENTATIONS,
        SESSION_MOMENTS,
        STARTUP_MOMENT,
        PERFORMANCE_CPU,
        PERFORMANCE_NETWORK,
        PERFORMANCE_ANR,
        PERFORMANCE_CONNECTIVITY,
        PERFORMANCE_CURRENT_DISK_USAGE,
        USER_PERSONAS,
        LOG_PROPERTIES,
        LOGS_INFO,
        LOGS_WARN
    )

    private val otelCfg = OpenTelemetryConfiguration(
        SpanSinkImpl(),
        LogSinkImpl(),
        SystemInfo(),
        "my-id"
    )

    private lateinit var sessionBehavior: SessionBehavior
    private var cfg: RemoteConfig? = RemoteConfig()

    @Before
    fun setUp() {
        localConfig = LocalConfig("default test app Id", false, SdkLocalConfig())
        sessionBehavior = fakeSessionBehavior { cfg }
        configService = FakeConfigService(sessionBehavior = fakeSessionBehavior { cfg })
        logger = EmbLoggerImpl()
        gatingService = EmbraceGatingService(
            configService,
            EmbLoggerImpl()
        )
    }

    @Test
    fun `test gating feature disabled by default for sessions`() {
        val sessionMessage = SessionMessage(fakeSession().copy(properties = emptyMap()))
        val result = gatingService.gateSessionMessage(sessionMessage)

        // result shouldn't be sanitized.
        assertNotNull(result.session.properties)
    }

    @Test
    fun `test error logs not empty`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(FULL_SESSION_ERROR_LOGS)
        )

        val sessionMessage = SessionMessage(
            fakeSession().copy(
                errorLogIds = listOf("id1"),
                properties = emptyMap()
            )
        )

        val result = gatingService.gateSessionMessage(sessionMessage)

        // result shouldn't be sanitized.
        assertEquals("id1", result.session.errorLogIds?.at(0))
        assertNotNull(result.session.properties)
    }

    @Test
    fun `test crashReportId not null`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(FULL_SESSION_ERROR_LOGS)
        )

        val sessionMessage = SessionMessage(
            fakeSession().copy(
                crashReportId = "crashReportId",
                properties = emptyMap()
            )
        )

        val result = gatingService.gateSessionMessage(sessionMessage)

        // result shouldn't be sanitized.
        assertEquals("crashReportId", result.session.crashReportId)
        assertNotNull(result.session.properties)
    }

    @Test
    fun `test gating feature from local and remote config`() {
        localConfig = LocalConfigParser.buildConfig(
            "GrCPU",
            false,
            "{\"session\": {\"components\": [" +
                "\"br_tb\"," +
                "\"br_vb\"," +
                "\"br_cv\"," +
                "\"br_wv\"," +
                "\"br_cb\"," +
                "\"log_pr\"," +
                "\"s_props\"," +
                "\"s_oc\"," +
                "\"s_tr\"," +
                "\"ur_per\"," +
                "\"pr_anr\"," +
                "\"pr_ns\"," +
                "\"pr_nr\"," +
                "\"pr_cp\"," +
                "\"pr_mw\"," +
                "\"pr_ds\"," +
                "\"log_in\"," +
                "\"log_war\"," +
                "\"s_mts\"," +
                "\"mts_st\"" +
                "]" +
                "}}",
            EmbraceSerializer(),
            otelCfg,
            EmbLoggerImpl()
        )

        cfg = buildCustomRemoteConfig(
            enabledComponentsFull,
            null
        )

        gatingService = EmbraceGatingService(
            configService,
            logger
        )
    }

    @Test
    fun `test full session events config`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf(FULL_SESSION_CRASHES, FULL_SESSION_ERROR_LOGS)
        )

        assertTrue(sessionBehavior.shouldSendFullForCrash())
        assertTrue(sessionBehavior.shouldSendFullForErrorLog())
    }

    @Test
    fun `test gate session properties for Session`() {
        val session = fakeSession().copy(
            properties = mapOf("key" to "value")
        )
        cfg = buildCustomRemoteConfig(setOf(SESSION_PROPERTIES), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNotNull(sanitizedMessage.session.properties)
    }

    @Test
    fun `test gate tracked orientations for Session`() {
        val session = fakeSession().copy(
            orientations = listOf(Orientation(1, 123123123))
        )
        cfg = buildCustomRemoteConfig(setOf(SESSION_ORIENTATIONS), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNotNull(sanitizedMessage.session.orientations)
    }

    @Test
    fun `test gate user termination for Session`() {
        val session = fakeSession().copy(
            terminationTime = 123123123,
            isReceivedTermination = true
        )
        cfg = buildCustomRemoteConfig(setOf(SESSION_USER_TERMINATION), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNotNull(sanitizedMessage.session.terminationTime)
        assertTrue(checkNotNull(sanitizedMessage.session.isReceivedTermination))
    }

    @Test
    fun `test do not gate startup moment for Session`() {
        val session = fakeSession().copy(
            startupDuration = 123123123,
            startupThreshold = 321321321
        )
        cfg = buildCustomRemoteConfig(setOf(STARTUP_MOMENT), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNotNull(sanitizedMessage.session.startupDuration)
        assertNotNull(sanitizedMessage.session.startupThreshold)
    }

    @Test
    fun `test gate startup moment for Session`() {
        val session = fakeSession().copy(
            startupDuration = 123123123,
            startupThreshold = 321321321
        )
        cfg = buildCustomRemoteConfig(setOf(), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNull(sanitizedMessage.session.startupDuration)
        assertNull(sanitizedMessage.session.startupThreshold)
    }

    @Test
    fun `test do not gate logs for Session`() {
        val session = fakeSession().copy(
            infoLogIds = listOf("INFO-LOG"),
            infoLogsAttemptedToSend = 1,
            warningLogIds = listOf("WARNING-LOG"),
            warnLogsAttemptedToSend = 1
        )
        cfg = buildCustomRemoteConfig(setOf(LOGS_WARN, LOGS_INFO), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        val infoIds = checkNotNull(sanitizedMessage.session.infoLogIds)
        assertTrue(sanitizedMessage.session.infoLogsAttemptedToSend == 1)
        assertTrue(infoIds.contains("INFO-LOG"))
        val warnIds = checkNotNull(sanitizedMessage.session.warningLogIds)
        assertTrue(sanitizedMessage.session.warnLogsAttemptedToSend == 1)
        assertTrue(warnIds.contains("WARNING-LOG"))
    }

    @Test
    fun `test gate logs for Session`() {
        val session = fakeSession().copy(
            infoLogIds = listOf("INFO-LOG"),
            infoLogsAttemptedToSend = 1,
            warningLogIds = listOf("WARNING-LOG"),
            warnLogsAttemptedToSend = 1
        )
        cfg = buildCustomRemoteConfig(setOf(), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNull(sanitizedMessage.session.infoLogIds)
        assertFalse(sanitizedMessage.session.infoLogsAttemptedToSend == 1)
        assertNull(sanitizedMessage.session.warningLogIds)
        assertFalse(sanitizedMessage.session.warnLogsAttemptedToSend == 1)
    }

    @Test
    fun `test do not gate moment event for Session`() {
        val session = fakeSession().copy(eventIds = listOf("MOMENT-ID"))
        cfg = buildCustomRemoteConfig(setOf(SESSION_MOMENTS), null)

        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        val ids = checkNotNull(sanitizedMessage.session.eventIds)
        assertTrue(ids.contains("MOMENT-ID"))
    }

    @Test
    fun `test gate moment event for Session`() {
        val session = fakeSession().copy(eventIds = listOf("MOMENT-ID"))
        cfg = buildCustomRemoteConfig(setOf(), null)
        val sessionMessage = SessionMessage(session)
        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        assertNull(sanitizedMessage.session.eventIds)
    }

    @Test
    fun `test gate user personas for Event`() {
        val userInfo = UserInfo(personas = setOf("persona"))

        val eventMessage = EventMessage(
            event = Event(
                eventId = Uuid.getEmbUuid(),
                timestamp = 100L,
                type = EventType.INFO_LOG
            ),
            userInfo = userInfo,
            performanceInfo = PerformanceInfo()
        )

        cfg = buildCustomRemoteConfig(setOf(USER_PERSONAS), null)

        val sanitizedMessage = gatingService.gateEventMessage(eventMessage)

        assertNotNull(sanitizedMessage.userInfo?.personas)
    }

    @Test
    fun `test gate user personas for Session`() {
        val userInfo = UserInfo(personas = setOf("persona"))

        val sessionMessage = SessionMessage(
            session = fakeSession(),
            userInfo = userInfo,
            performanceInfo = PerformanceInfo()
        )

        cfg = buildCustomRemoteConfig(setOf(USER_PERSONAS), null)

        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)
        assertNotNull(sanitizedMessage.userInfo?.personas)
    }

    @Test
    fun `test gate performance info for Session`() {
        val performanceInfo = PerformanceInfo(
            diskUsage = DiskUsage(100, null)
        )

        val sessionPerformanceInfo = performanceInfo.copy()

        val sessionMessage = SessionMessage(
            session = fakeSession(),
            performanceInfo = sessionPerformanceInfo
        )

        cfg = buildCustomRemoteConfig(
            setOf(
                PERFORMANCE_ANR,
                PERFORMANCE_CONNECTIVITY,
                PERFORMANCE_CPU,
                PERFORMANCE_NETWORK,
                PERFORMANCE_CURRENT_DISK_USAGE
            ),
            null
        )

        val sanitizedMessage = gatingService.gateSessionMessage(sessionMessage)

        val perfInfo = checkNotNull(sanitizedMessage.performanceInfo)
        assertNotNull(perfInfo.diskUsage)
    }

    @Test
    fun `test public methods`() {
        cfg = buildCustomRemoteConfig(
            setOf(),
            setOf()
        )

        // the enabled components are empty, so it should gate everything
        assertTrue(sessionBehavior.shouldGateMoment())
        assertTrue(sessionBehavior.shouldGateInfoLog())
        assertTrue(sessionBehavior.shouldGateWarnLog())
        assertTrue(sessionBehavior.shouldGateStartupMoment())
        assertTrue(sessionBehavior.shouldGateLogProperties())
        assertTrue(sessionBehavior.shouldGateSessionProperties())
    }

    private fun buildCustomRemoteConfig(components: Set<String>?, fullSessionEvents: Set<String>?) =
        RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                isEnabled = true,
                sessionComponents = components,
                fullSessionEvents = fullSessionEvents
            )
        )
}
