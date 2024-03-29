package io.embrace.android.embracesdk.internal.logs

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal class EmbraceLogServiceTest {

    companion object {
        private lateinit var logWriter: FakeLogWriter
        private lateinit var metadataService: FakeMetadataService
        private lateinit var configService: ConfigService
        private lateinit var sessionIdTracker: FakeSessionIdTracker
        private lateinit var sessionProperties: EmbraceSessionProperties
        private lateinit var executor: ExecutorService
        private lateinit var tick: AtomicLong
        private lateinit var clock: Clock

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            metadataService = FakeMetadataService()
            sessionIdTracker = FakeSessionIdTracker()
            executor = Executors.newSingleThreadExecutor()
            tick = AtomicLong(1609823408L)
            clock = Clock { tick.incrementAndGet() }
        }
    }

    private lateinit var cfg: RemoteConfig

    @Before
    fun setUp() {
        logWriter = FakeLogWriter()
        sessionIdTracker.setActiveSessionId("session-123", true)
        sessionProperties = EmbraceSessionProperties(
            FakePreferenceService(),
            FakeConfigService()
        )
        cfg = RemoteConfig()
        configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior {
                cfg
            },
            logMessageBehavior = fakeLogMessageBehavior {
                cfg.logConfig
            },
            dataCaptureEventBehavior = fakeDataCaptureEventBehavior {
                cfg
            }
        )
    }

    @Test
    fun testSimpleLog() {
        val logService = getLogService()

        val props = mapOf("foo" to "bar")
        logService.log("Hello world", Severity.INFO, props)
        logService.log("Warning world", Severity.WARNING, null)
        logService.log("Hello errors", Severity.ERROR, null)

        val logs = logWriter.logEvents
        assertEquals(3, logs.size)
        val first = logs[0]
        assertEquals("Hello world", first.message)
        assertEquals(Severity.INFO, first.severity)
        assertEquals("bar", first.attributes["foo"])
        assertNotNull(first.attributes["emb.log_id"])
        assertEquals("session-123", first.attributes["emb.session_id"])
        assertNull(first.attributes["emb.exception_type"])

        val second = logs[1]
        assertEquals("Warning world", second.message)
        assertEquals(Severity.WARNING, second.severity)
        assertNotNull(second.attributes["emb.log_id"])
        assertEquals("session-123", second.attributes["emb.session_id"])
        assertNull(second.attributes["emb.exception_type"])

        val third = logs[2]
        assertEquals("Hello errors", third.message)
        assertEquals(Severity.ERROR, third.severity)
        assertNotNull(third.attributes["emb.log_id"])
        assertEquals("session-123", third.attributes["emb.session_id"])
        assertNull(third.attributes["emb.exception_type"])
        third.assertIsType(EmbType.System.Log)
    }

    @Test
    fun testExceptionLog() {
        val logService = getLogService()
        val exception = NullPointerException("exception message")

        logService.logException(
            "Hello world",
            Severity.WARNING,
            LogExceptionType.HANDLED,
            null,
            exception.stackTrace,
            null,
            AppFramework.NATIVE,
            null,
            null,
            exception.javaClass.simpleName,
            exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals(0, logService.getUnhandledExceptionsSent())
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertEquals("NullPointerException", log.attributes["emb.exception_name"])
        assertEquals("exception message", log.attributes["emb.exception_message"])
        assertEquals(AppFramework.NATIVE.value.toString(), log.attributes["emb.app_framework"])
        assertNotNull(log.attributes["emb.log_id"])
        assertEquals("session-123", log.attributes["emb.session_id"])
        assertEquals(LogExceptionType.HANDLED.value, log.attributes["emb.exception_type"])
        log.assertIsType(EmbType.System.Log)
    }

    @Test
    fun testUnhandledExceptionLog() {
        val logService = getLogService()
        val exception = NullPointerException("exception message")

        logService.logException(
            "Hello world",
            Severity.WARNING,
            LogExceptionType.UNHANDLED,
            null,
            exception.stackTrace,
            null,
            AppFramework.UNITY,
            null,
            null,
            exception.javaClass.simpleName,
            exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals(1, logService.getUnhandledExceptionsSent())
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertEquals("NullPointerException", log.attributes["emb.exception_name"])
        assertEquals("exception message", log.attributes["emb.exception_message"])
        assertEquals(AppFramework.UNITY.value.toString(), log.attributes["emb.app_framework"])
        assertNotNull(log.attributes["emb.log_id"])
        assertEquals("session-123", log.attributes["emb.session_id"])
        assertEquals(LogExceptionType.UNHANDLED.value, log.attributes["emb.exception_type"])
        log.assertIsType(EmbType.System.Log)
    }

    @Test
    fun `test session properties are added correctly to a log`() {
        sessionProperties.add("session_prop_1", "session_val_1", false)
        sessionProperties.add("session_prop_2", "session_val_2", false)
        val logService = getLogService()
        logService.log("Hello world", Severity.INFO, null)

        val log = logWriter.logEvents.single()
        assertEquals("session_val_1", log.attributes["emb.properties.session_prop_1"])
        assertEquals("session_val_2", log.attributes["emb.properties.session_prop_2"])
    }

    @Test
    fun `Embrace properties can not be overridden by custom properties`() {
        val logService = getLogService()
        val props = mapOf("emb.session_id" to "session-456")
        logService.log("Hello world", Severity.INFO, props)

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertNotNull(log.attributes["emb.log_id"])
        assertEquals("session-123", log.attributes["emb.session_id"])
    }

    @Test
    fun testDefaultMaxMessageCountLimits() {
        val logService = getLogService()

        repeat(500) { k ->
            logService.log("Test info $k", Severity.INFO, null)
            logService.log("Test warning $k", Severity.WARNING, null)
            logService.log("Test error $k", Severity.ERROR, null)
        }

        assertEquals(100, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, logService.getInfoLogsAttemptedToSend())
        assertEquals(100, logService.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, logService.getWarnLogsAttemptedToSend())
        assertEquals(250, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, logService.getErrorLogsAttemptedToSend())
    }

    @Test
    fun testCustomMaxMessageCountLimits() {
        cfg = cfg.copy(
            logConfig = LogRemoteConfig(
                logInfoLimit = 50,
                logWarnLimit = 110,
                logErrorLimit = 150
            )
        )

        val logService = getLogService()

        repeat(500) { k ->
            logService.log("Test info $k", Severity.INFO, null)
            logService.log("Test warning $k", Severity.WARNING, null)
            logService.log("Test error $k", Severity.ERROR, null)
        }

        assertEquals(50, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, logService.getInfoLogsAttemptedToSend())
        assertEquals(110, logService.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, logService.getWarnLogsAttemptedToSend())
        assertEquals(150, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(500, logService.getErrorLogsAttemptedToSend())
    }

    @Test
    fun testDefaultMaxMessageLength() {
        val logMessageService = getLogService()
        logMessageService.log("Hi".repeat(65), Severity.INFO, null)

        val log = logWriter.logEvents.single()
        Assert.assertTrue(log.message == "Hi".repeat(62) + "H...")
    }

    @Test
    fun testCustomMaxMessageLength() {
        cfg = cfg.copy(
            logConfig = LogRemoteConfig(
                logMessageMaximumAllowedLength = 50,
                logInfoLimit = 50
            )
        )

        val logMessageService = getLogService()
        logMessageService.log("Hi".repeat(50), Severity.INFO, null)

        val log = logWriter.logEvents.single()
        Assert.assertTrue(log.message == "Hi".repeat(23) + "H...")
    }

    @Test
    fun testLoggingUnityMessage() {
        val logMessageService = getLogService(appFramework = AppFramework.UNITY)

        logMessageService.logException(
            "Unity".repeat(1000),
            Severity.INFO,
            LogExceptionType.HANDLED,
            null,
            null,
            "my stacktrace",
            AppFramework.UNITY,
            null,
            null,
            null,
            null
        )

        val log = logWriter.logEvents.single()
        assertEquals("Unity".repeat(1000), log.message) // log limit higher on unity
        // TBD: Assert stacktrace
        assertEquals(AppFramework.UNITY.value.toString(), log.attributes["emb.app_framework"])
        assertEquals(LogExceptionType.HANDLED.value, log.attributes["emb.exception_type"])
        // TBD: Assert unhandled exceptions
        // assertEquals(0, logMessageService.getUnhandledExceptionsSent())
    }

    @Test
    fun testCleanCollections() {
        val logService = getLogService()
        repeat(10) { k ->
            logService.log("Test info $k", Severity.INFO, null)
            logService.log("Test warning $k", Severity.WARNING, null)
            logService.log("Test error $k", Severity.ERROR, null)
        }
        assertEquals(10, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(10, logService.getInfoLogsAttemptedToSend())
        assertEquals(10, logService.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(10, logService.getWarnLogsAttemptedToSend())
        assertEquals(10, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(10, logService.getErrorLogsAttemptedToSend())

        logService.cleanCollections()

        assertEquals(0, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(0, logService.getInfoLogsAttemptedToSend())
        assertEquals(0, logService.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(0, logService.getWarnLogsAttemptedToSend())
        assertEquals(0, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(0, logService.getErrorLogsAttemptedToSend())
    }

    // If the session components are null, no gating should be applied
    @Test
    fun testGatingWithNullSessionComponents() {
        cfg = buildCustomRemoteConfig(null)
        val logService = getLogService()

        logService.log("Test info log", Severity.INFO, null)
        logService.log("Test warning log", Severity.WARNING, null)

        assertEquals(2, logWriter.logEvents.size)
        assertEquals(1, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(1, logService.getInfoLogsAttemptedToSend())
        assertEquals(1, logService.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(1, logService.getWarnLogsAttemptedToSend())
    }

    // If the session components exists, only keys present should be allowed to be sent
    @Test
    fun testGatingWithEmptySessionComponents() {
        cfg = buildCustomRemoteConfig(
            setOf()
        )
        val logService = getLogService()

        logService.log("Test info log", Severity.INFO, null)
        logService.log("Test warning log", Severity.WARNING, null)

        assertEquals(0, logWriter.logEvents.size)
        assertEquals(0, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(0, logService.getInfoLogsAttemptedToSend())
    }

    // If the session components exists, only keys present should be allowed to be sent
    @Test
    fun testGatingWithLogKeysInSessionComponents() {
        cfg = buildCustomRemoteConfig(
            setOf(SessionGatingKeys.LOGS_INFO, SessionGatingKeys.LOGS_WARN)
        )
        val logService = getLogService()

        logService.log("Test info log", Severity.INFO, null)
        logService.log("Test warning log", Severity.WARNING, null)

        assertEquals(2, logWriter.logEvents.size)
        assertEquals(1, logService.findInfoLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(1, logService.getInfoLogsAttemptedToSend())
        assertEquals(1, logService.findWarningLogIds(0L, Long.MAX_VALUE).size)
        assertEquals(1, logService.getWarnLogsAttemptedToSend())
    }

    private fun getLogService(appFramework: AppFramework = AppFramework.NATIVE): EmbraceLogService {
        return EmbraceLogService(
            logWriter,
            metadataService,
            configService,
            appFramework,
            sessionIdTracker,
            sessionProperties,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            clock,
        )
    }

    private fun buildCustomRemoteConfig(components: Set<String>?, fullSessionEvents: Set<String>? = null) =
        RemoteConfig(
            sessionConfig = SessionRemoteConfig(
                isEnabled = true,
                sessionComponents = components,
                fullSessionEvents = fullSessionEvents
            )
        )
}
