package io.embrace.android.embracesdk.internal.logs

import com.google.common.util.concurrent.MoreExecutors
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.gating.SessionGatingKeys
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.FlutterException.embFlutterExceptionContext
import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System.FlutterException.embFlutterExceptionLibrary
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

internal class EmbraceLogServiceTest {

    private lateinit var cfg: RemoteConfig
    private lateinit var logService: LogService
    private lateinit var logWriter: FakeLogWriter
    private lateinit var configService: FakeConfigService
    private lateinit var gatingService: GatingService
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var tick: AtomicLong
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        logWriter = FakeLogWriter()
        tick = AtomicLong(1609823408L)
        clock = Clock { tick.incrementAndGet() }
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
        logService = getLogServiceWithFramework()
    }

    @Test
    fun testSimpleLog() {
        val props = mapOf("foo" to "bar")
        logWithoutException("Hello world", EventType.INFO_LOG, props)
        logWithoutException("Warning world", EventType.WARNING_LOG, props)
        logWithoutException("Hello errors", EventType.ERROR_LOG, props)

        val logs = logWriter.logEvents
        assertEquals(3, logs.size)
        val first = logs[0]
        assertEquals("Hello world", first.message)
        assertEquals(Severity.INFO, first.severity)
        assertEquals("bar", first.schemaType.attributes()["foo"])
        assertNotNull(first.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertNull(first.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])

        val second = logs[1]
        assertEquals("Warning world", second.message)
        assertEquals(Severity.WARNING, second.severity)
        assertNotNull(second.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertNull(second.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])

        val third = logs[2]
        assertEquals("Hello errors", third.message)
        assertEquals(Severity.ERROR, third.severity)
        assertNotNull(third.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertNull(third.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])
        third.assertIsType(EmbType.System.Log)
    }

    @Test
    fun testExceptionLog() {
        val exception = NullPointerException("exception message")

        logService.log(
            message = "Hello world",
            type = EventType.WARNING_LOG,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTraceElements = exception.stackTrace,
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertNotNull(log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertEquals(LogExceptionType.HANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
        assertEquals("NullPointerException", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])
        assertEquals("exception message", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_MESSAGE.key])
        assertEquals(
            exception.stackTrace.toExceptionSchema(),
            log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key]
        )
        log.assertIsType(EmbType.System.Exception)
    }

    @Test
    fun testFlutterExceptionLog() {
        val logService = getLogServiceWithFramework(AppFramework.FLUTTER)
        val exception = NullPointerException("exception message")

        logService.log(
            message = "Dart error",
            type = EventType.ERROR_LOG,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTraceElements = exception.stackTrace,
            customStackTrace = null,
            context = "context",
            library = "library",
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Dart error", log.message)
        assertEquals(Severity.ERROR, log.severity)
        assertNotNull(log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertEquals(LogExceptionType.HANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
        assertEquals("NullPointerException", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])
        assertEquals("exception message", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_MESSAGE.key])
        assertEquals(
            exception.stackTrace.toExceptionSchema(),
            log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key]
        )
        assertEquals("context", log.schemaType.attributes()[embFlutterExceptionContext.name])
        assertEquals("library", log.schemaType.attributes()[embFlutterExceptionLibrary.name])
        log.assertIsType(EmbType.System.FlutterException)
    }

    @Test
    fun testUnhandledExceptionLog() {
        val exception = NullPointerException("exception message")

        logService.log(
            message = "Hello world",
            type = EventType.WARNING_LOG,
            logExceptionType = LogExceptionType.UNHANDLED,
            properties = null,
            stackTraceElements = exception.stackTrace,
            customStackTrace = null,
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.WARNING, log.severity)
        assertEquals("NullPointerException", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])
        assertEquals("exception message", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_MESSAGE.key])
        assertEquals(
            exception.stackTrace.toExceptionSchema(),
            log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key]
        )
        assertNotNull(log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertEquals(LogExceptionType.UNHANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
        log.assertIsType(EmbType.System.Exception)
    }

    @Test
    fun `test session properties are added correctly to a log`() {
        sessionProperties.add("session_prop_1", "session_val_1", false)
        sessionProperties.add("session_prop_2", "session_val_2", false)

        logService.log(
            message = "Hello world",
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE
        )

        val log = logWriter.logEvents.single()
        assertEquals("session_val_1", log.schemaType.attributes().getSessionProperty("session_prop_1"))
        assertEquals("session_val_2", log.schemaType.attributes().getSessionProperty("session_prop_2"))
    }

    @Test
    fun `Embrace properties can not be overridden by custom properties`() {
        val props = mapOf(LogIncubatingAttributes.LOG_RECORD_UID.key to "fakeUid")
        logService.log(
            message = "Hello world",
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE,
            properties = props
        )

        val log = logWriter.logEvents.single()
        assertEquals("Hello world", log.message)
        assertEquals(Severity.INFO, log.severity)
        assertNotNull(log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertNotEquals("fakeUid", log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])
    }

    @Test
    fun testDefaultMaxMessageCountLimits() {
        repeat(500) { k ->
            logWithoutException("Test info $k", EventType.INFO_LOG)
            logWithoutException("Test warning $k", EventType.WARNING_LOG)
            logWithoutException("Test error $k", EventType.ERROR_LOG)
        }

        assertEquals(250, logService.findErrorLogIds().size)
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

        logService = getLogServiceWithFramework()

        repeat(500) { k ->
            logWithoutException("Test info $k", EventType.INFO_LOG)
            logWithoutException("Test warning $k", EventType.WARNING_LOG)
            logWithoutException("Test error $k", EventType.ERROR_LOG)
        }

        assertEquals(150, logService.findErrorLogIds().size)
    }

    @Test
    fun testDefaultMaxMessageLength() {
        logService.log(
            message = "Hi".repeat(65),
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE,
        )

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

        logService = getLogServiceWithFramework()

        logService.log(
            message = "Hi".repeat(50),
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.NONE,
        )

        val log = logWriter.logEvents.single()
        Assert.assertTrue(log.message == "Hi".repeat(23) + "H...")
    }

    @Test
    fun testLoggingUnityMessage() {
        val logService = getLogServiceWithFramework(appFramework = AppFramework.UNITY)

        logService.log(
            message = "Unity".repeat(1000),
            type = EventType.INFO_LOG,
            logExceptionType = LogExceptionType.HANDLED,
            customStackTrace = "my stacktrace",
        )

        val log = logWriter.logEvents.single()
        assertEquals("Unity".repeat(1000), log.message) // log limit higher on unity
        assertEquals(Severity.INFO, log.severity)
        assertNotNull(log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key])

        assertEquals("my stacktrace", log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key])
        assertEquals(null, log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_TYPE.key])
        assertEquals(null, log.schemaType.attributes()[ExceptionIncubatingAttributes.EXCEPTION_MESSAGE.key])
        assertEquals(LogExceptionType.HANDLED.value, log.schemaType.attributes()[embExceptionHandling.name])
    }

    @Test
    fun testCleanCollections() {
        repeat(10) { k ->
            logWithoutException("Test info $k", EventType.INFO_LOG)
            logWithoutException("Test warning $k", EventType.WARNING_LOG)
            logWithoutException("Test error $k", EventType.ERROR_LOG)
        }
        assertEquals(10, logService.findErrorLogIds().size)

        logService.cleanCollections()

        assertEquals(0, logService.findErrorLogIds().size)
    }

    // If the session components are null, no gating should be applied
    @Test
    fun testGatingWithNullSessionComponents() {
        cfg = buildCustomRemoteConfig(null)

        logService = getLogServiceWithFramework()

        logWithoutException("Test info log", EventType.INFO_LOG)
        logWithoutException("Test warning log", EventType.WARNING_LOG)

        assertEquals(2, logWriter.logEvents.size)
    }

    // If the session components exists, only keys present should be allowed to be sent
    @Test
    fun testGatingWithEmptySessionComponents() {
        cfg = buildCustomRemoteConfig(
            setOf()
        )

        logService = getLogServiceWithFramework()

        logWithoutException("Test info log", EventType.INFO_LOG)
        logWithoutException("Test warning log", EventType.WARNING_LOG)

        assertEquals(0, logWriter.logEvents.size)
    }

    // If the session components exists, only keys present should be allowed to be sent
    @Test
    fun testGatingWithLogKeysInSessionComponents() {
        cfg = buildCustomRemoteConfig(
            setOf(SessionGatingKeys.LOGS_INFO, SessionGatingKeys.LOGS_WARN)
        )

        logService = getLogServiceWithFramework()

        logWithoutException("Test info log", EventType.INFO_LOG)
        logWithoutException("Test warning log", EventType.WARNING_LOG)

        assertEquals(2, logWriter.logEvents.size)
    }

    @Test
    fun testWrongEventType() {
        // The log service can handle only INFO_LOG, WARNING_LOG and ERROR_LOG event types
        logService.log(
            message = "simple log",
            type = EventType.CRASH,
            logExceptionType = LogExceptionType.HANDLED,
            properties = null,
            stackTraceElements = null,
            customStackTrace = null,
            context = null,
            library = null,
            exceptionName = null,
            exceptionMessage = null
        )
        assertEquals(0, logWriter.logEvents.size)
    }

    private fun getLogServiceWithFramework(appFramework: AppFramework = AppFramework.NATIVE): EmbraceLogService {
        configService.appFramework = appFramework
        return EmbraceLogService(
            logWriter,
            configService,
            sessionProperties,
            BackgroundWorker(MoreExecutors.newDirectExecutorService()),
            EmbLoggerImpl(),
            clock,
            EmbraceSerializer()
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

    private fun logWithoutException(message: String, type: EventType, properties: Map<String, String>? = null) {
        logService.log(
            message = message,
            type = type,
            logExceptionType = LogExceptionType.NONE,
            properties = properties
        )
    }
}

// this is quite ugly, but I didn't want to use the serializer
private fun Array<StackTraceElement>.toExceptionSchema(): String {
    return "[\"${this.joinToString(separator = "\",\"")}\"]"
}
