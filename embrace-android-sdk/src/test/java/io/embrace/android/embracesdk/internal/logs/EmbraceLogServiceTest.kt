package io.embrace.android.embracesdk.internal.logs

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.EmbType.System.FlutterException.embFlutterExceptionContext
import io.embrace.android.embracesdk.arch.schema.EmbType.System.FlutterException.embFlutterExceptionLibrary
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.opentelemetry.exceptionMessage
import io.embrace.android.embracesdk.opentelemetry.exceptionStacktrace
import io.embrace.android.embracesdk.opentelemetry.exceptionType
import io.embrace.android.embracesdk.opentelemetry.logRecordUid
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        private lateinit var configService: ConfigService
        private lateinit var gatingService: GatingService
        private lateinit var sessionProperties: EmbraceSessionProperties
        private lateinit var executor: ExecutorService
        private lateinit var tick: AtomicLong
        private lateinit var clock: Clock

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            executor = Executors.newSingleThreadExecutor()
            tick = AtomicLong(1609823408L)
            clock = Clock { tick.incrementAndGet() }
        }
    }

    private lateinit var cfg: RemoteConfig

    @Before
    fun setUp() {
        logWriter = FakeLogWriter()
        sessionProperties = EmbraceSessionProperties(
            FakePreferenceService(),
            FakeConfigService(),
            EmbLoggerImpl()
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
        gatingService = FakeGatingService(configService)
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
        assertEquals("bar", first.schemaType.attributes()["foo"])
        assertNotNull(first.schemaType.attributes()[logRecordUid.key])
        assertNull(first.schemaType.attributes()[exceptionType.key])

        val second = logs[1]
        assertEquals("Warning world", second.message)
        assertEquals(Severity.WARNING, second.severity)
        assertNotNull(second.schemaType.attributes()[logRecordUid.key])
        assertNull(second.schemaType.attributes()[exceptionType.key])

        val third = logs[2]
        assertEquals("Hello errors", third.message)
        assertEquals(Severity.ERROR, third.severity)
        assertNotNull(third.schemaType.attributes()[logRecordUid.key])
        assertNull(third.schemaType.attributes()[exceptionType.key])
        third.assertIsType(EmbType.System.Log)
    }

    @Test
    fun testExceptionLog() {
        val logService = getLogService()
        val exception = NullPointerException("exception message")

        logService.logException(
            message = "Hello world",
            severity = Severity.WARNING,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTrace = exception.stackTrace.joinToString(", "),
            framework = AppFramework.NATIVE,
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertNotNull(log.schemaType.attributes()[logRecordUid.key])
        assertEquals(LogExceptionType.HANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
        assertEquals("NullPointerException", log.schemaType.attributes()[exceptionType.key])
        assertEquals("exception message", log.schemaType.attributes()[exceptionMessage.key])
        assertEquals(exception.stackTrace.joinToString(", "), log.schemaType.attributes()[exceptionStacktrace.key])
        log.assertIsType(EmbType.System.Exception)
    }

    @Test
    fun testFlutterExceptionLog() {
        val logService = getLogService()
        val exception = NullPointerException("exception message")

        logService.logFlutterException(
            message = "Hello world",
            severity = Severity.WARNING,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTrace = exception.stackTrace.joinToString(", "),
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
            context = "context",
            library = "library",
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertNotNull(log.schemaType.attributes()[logRecordUid.key])
        assertEquals(LogExceptionType.HANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
        assertEquals("NullPointerException", log.schemaType.attributes()[exceptionType.key])
        assertEquals("exception message", log.schemaType.attributes()[exceptionMessage.key])
        assertEquals(exception.stackTrace.joinToString(", "), log.schemaType.attributes()[exceptionStacktrace.key])
        assertEquals("context", log.schemaType.attributes()[embFlutterExceptionContext.name])
        assertEquals("library", log.schemaType.attributes()[embFlutterExceptionLibrary.name])
        log.assertIsType(EmbType.System.FlutterException)
    }

    @Test
    fun testUnhandledExceptionLog() {
        val logService = getLogService()
        val exception = NullPointerException("exception message")

        logService.logException(
            message = "Hello world",
            severity = Severity.WARNING,
            logExceptionType = LogExceptionType.UNHANDLED,
            properties = null,
            stackTrace = exception.stackTrace.joinToString(", "),
            framework = AppFramework.UNITY,
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertEquals("NullPointerException", log.schemaType.attributes()[exceptionType.key])
        assertEquals("exception message", log.schemaType.attributes()[exceptionMessage.key])
        assertEquals(exception.stackTrace.joinToString(", "), log.schemaType.attributes()[exceptionStacktrace.key])
        assertNotNull(log.schemaType.attributes()[logRecordUid.key])
        assertEquals(LogExceptionType.UNHANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
        log.assertIsType(EmbType.System.Exception)
    }

    @Test
    fun `test session properties are added correctly to a log`() {
        sessionProperties.add("session_prop_1", "session_val_1", false)
        sessionProperties.add("session_prop_2", "session_val_2", false)
        val logService = getLogService()
        logService.log("Hello world", Severity.INFO, null)

        val log = logWriter.logEvents.single()
        assertEquals("session_val_1", log.schemaType.attributes().getSessionProperty("session_prop_1"))
        assertEquals("session_val_2", log.schemaType.attributes().getSessionProperty("session_prop_2"))
    }

    @Test
    fun `Embrace properties can not be overridden by custom properties`() {
        val logService = getLogService()
        val props = mapOf(logRecordUid.key to "fakeUid")
        logService.log("Hello world", Severity.INFO, props)

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertNotNull(log.schemaType.attributes()[logRecordUid.key])
        assertNotEquals("fakeUid", log.schemaType.attributes()[logRecordUid.key])
    }

    @Test
    fun testDefaultMaxMessageCountLimits() {
        val logService = getLogService()

        repeat(500) { k ->
            logService.log("Test info $k", Severity.INFO, null)
            logService.log("Test warning $k", Severity.WARNING, null)
            logService.log("Test error $k", Severity.ERROR, null)
        }

        assertEquals(250, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
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

        assertEquals(150, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
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
        val logService = getLogService(appFramework = AppFramework.UNITY)

        logService.logException(
            message = "Unity".repeat(1000),
            severity = Severity.INFO,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTrace = "my stacktrace",
            framework = AppFramework.UNITY,
            exceptionName = null,
            exceptionMessage = null,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Unity".repeat(1000), log.message) // log limit higher on unity
        assertEquals(Severity.INFO, log.severity)
        assertNotNull(log.schemaType.attributes()[logRecordUid.key])

        assertEquals("my stacktrace", log.schemaType.attributes()[exceptionStacktrace.key])
        assertEquals(null, log.schemaType.attributes()[exceptionType.key])
        assertEquals(null, log.schemaType.attributes()[exceptionMessage.key])
        assertEquals(LogExceptionType.HANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
    }

    @Test
    fun testCleanCollections() {
        val logService = getLogService()
        repeat(10) { k ->
            logService.log("Test info $k", Severity.INFO, null)
            logService.log("Test warning $k", Severity.WARNING, null)
            logService.log("Test error $k", Severity.ERROR, null)
        }
        assertEquals(10, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)

        logService.cleanCollections()

        assertEquals(0, logService.findErrorLogIds(0L, Long.MAX_VALUE).size)
    }

    // If the session components are null, no gating should be applied
    @Test
    fun testGatingWithNullSessionComponents() {
        cfg = buildCustomRemoteConfig(null)
        val logService = getLogService()

        logService.log("Test info log", Severity.INFO, null)
        logService.log("Test warning log", Severity.WARNING, null)

        assertEquals(2, logWriter.logEvents.size)
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
    }

    private fun getLogService(appFramework: AppFramework = AppFramework.NATIVE): EmbraceLogService {
        return EmbraceLogService(
            logWriter,
            configService,
            appFramework,
            sessionProperties,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            EmbLoggerImpl(),
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
