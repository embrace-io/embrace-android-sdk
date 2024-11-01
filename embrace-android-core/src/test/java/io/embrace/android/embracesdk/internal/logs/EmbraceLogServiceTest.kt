package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.behavior.FakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.embExceptionHandling
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.opentelemetry.semconv.ExceptionAttributes
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceLogServiceTest {

    private lateinit var logService: EmbraceLogService
    private lateinit var fakeLogWriter: FakeLogWriter
    private lateinit var fakeSessionPropertiesService: FakeSessionPropertiesService
    private lateinit var fakeConfigService: FakeConfigService

    private val embraceSerializer = EmbraceSerializer()

    @Before
    fun setUp() {
        fakeConfigService = FakeConfigService(
            sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(listOf("password"))
        )
        fakeSessionPropertiesService = FakeSessionPropertiesService()
        fakeLogWriter = FakeLogWriter()

        logService = createEmbraceLogService()
    }

    private fun createEmbraceLogService() = EmbraceLogService(
        logWriter = fakeLogWriter,
        configService = fakeConfigService,
        sessionPropertiesService = fakeSessionPropertiesService,
        serializer = embraceSerializer,
    )

    @Test
    fun `sensitive properties are redacted`() {
        // given custom properties with a sensitive key
        val properties = mapOf("password" to "123456", "status" to "success")

        // when logging a message with those properties
        logService.log("message", Severity.INFO, LogExceptionType.NONE, properties)

        // then the sensitive key is redacted
        val log = fakeLogWriter.logEvents.single()
        val attributes = log.schemaType.attributes()
        assertEquals(REDACTED_LABEL, attributes["password"])
        assertEquals("success", attributes["status"])
    }

    @Test
    fun `telemetry attributes are set correctly, including session properties and LOG_RECORD_UID`() {
        // given a session with properties
        fakeSessionPropertiesService.props["someProperty"] = "someValue"
        logService = createEmbraceLogService()

        // when logging the message
        logService.log("message", Severity.INFO, LogExceptionType.NONE)

        // then the telemetry attributes are set correctly
        val log = fakeLogWriter.logEvents.single()
        val attributes = log.schemaType.attributes()
        assertEquals("someValue", attributes["emb.properties.someProperty"])
        assertTrue(attributes.containsKey(LogIncubatingAttributes.LOG_RECORD_UID.key))
    }

    @Test
    fun `Embrace properties can not be overridden by custom properties`() {
        val props = mapOf(LogIncubatingAttributes.LOG_RECORD_UID.key to "fakeUid")
        logService.log(
            message = "Hello world",
            severity = Severity.INFO,
            logExceptionType = LogExceptionType.NONE,
            properties = props
        )

        val log = fakeLogWriter.logEvents.single()
        assertNotEquals(
            "fakeUid",
            log.schemaType.attributes()[LogIncubatingAttributes.LOG_RECORD_UID.key]
        )
    }

    @Test
    fun `info and warning logs are gated correctly`() {
        // given a config that gates info and warning logs
        fakeConfigService = FakeConfigService(
            sessionBehavior = createSessionBehavior(
                remoteCfg = {
                    RemoteConfig(
                        sessionConfig = SessionRemoteConfig(
                            isEnabled = true,
                            sessionComponents = emptySet() // empty set will gate everything
                        )
                    )
                }
            )
        )
        logService = createEmbraceLogService()

        // when logging some messages
        logService.log("info", Severity.INFO, LogExceptionType.NONE)
        logService.log("warning!", Severity.WARNING, LogExceptionType.NONE)
        logService.log("error!", Severity.ERROR, LogExceptionType.NONE)

        // then only errors are logged
        assertEquals(1, fakeLogWriter.logEvents.size)
    }

    @Test
    fun `logs that exceed the allowed count are not logged`() {
        // given a config with log limits
        val testLogLimit = 5
        fakeConfigService = FakeConfigService(
            logMessageBehavior = FakeLogMessageBehavior(
                infoLogLimit = testLogLimit,
                warnLogLimit = testLogLimit,
                errorLogLimit = testLogLimit
            )
        )
        logService = createEmbraceLogService()

        // when logging exactly the allowed count of each type
        repeat(testLogLimit) {
            logService.log("info", Severity.INFO, LogExceptionType.NONE)
            logService.log("warning!", Severity.WARNING, LogExceptionType.NONE)
            logService.log("error!", Severity.ERROR, LogExceptionType.NONE)
        }

        // then the logs are all logged
        assertEquals(testLogLimit * 3, fakeLogWriter.logEvents.size)

        // when logging one more of each type
        logService.log("info", Severity.INFO, LogExceptionType.NONE)
        logService.log("warning!", Severity.WARNING, LogExceptionType.NONE)
        logService.log("error!", Severity.ERROR, LogExceptionType.NONE)

        // then the logs are not logged
        assertEquals(testLogLimit * 3, fakeLogWriter.logEvents.size)
    }

    @Test
    fun `a max length smaller than 3 does not add ellipsis`() {
        // given a config with a log message limit smaller than 3
        fakeConfigService = FakeConfigService(
            logMessageBehavior = FakeLogMessageBehavior(logMessageMaximumAllowedLength = 2)
        )
        logService = createEmbraceLogService()

        // when logging a message that exceeds the limit
        logService.log("message", Severity.INFO, LogExceptionType.NONE)

        // then the message is not ellipsized
        val log = fakeLogWriter.logEvents.single()
        assertEquals("me", log.message)
    }

    @Test
    fun `a log message bigger than the max length is trimmed`() {
        // given a config with message limit
        fakeConfigService = FakeConfigService(
            logMessageBehavior = FakeLogMessageBehavior(logMessageMaximumAllowedLength = 5)
        )
        logService = createEmbraceLogService()

        // when logging a message that exceeds the limit
        logService.log("abcdef", Severity.INFO, LogExceptionType.NONE)

        // then the message is trimmed
        val log = fakeLogWriter.logEvents.single()
        assertEquals("ab...", log.message)
    }

    @Test
    fun `log messages in Unity are trimmed to Unity max length`() {
        // given a config with message limit and app framework Unity
        fakeConfigService = FakeConfigService(
            appFramework = AppFramework.UNITY,
            logMessageBehavior = FakeLogMessageBehavior(logMessageMaximumAllowedLength = 5)
        )
        logService = createEmbraceLogService()

        // when logging a message that exceeds the logMessageMaximumAllowedLength
        logService.log("abcdef", Severity.INFO, LogExceptionType.NONE)

        // then the message is not trimmed
        val log = fakeLogWriter.logEvents.single()
        assertEquals("abcdef", log.message)

        // when logging a message that exceeds the Unity maximum allowed length
        val unityMaxAllowedLength = 16384
        logService.log(
            "a".repeat(unityMaxAllowedLength + 1),
            Severity.INFO,
            LogExceptionType.NONE
        )

        // then the message is trimmed
        val anotherLog = fakeLogWriter.logEvents[1]
        assertEquals("a".repeat(unityMaxAllowedLength - 3) + "...", anotherLog.message)
    }

    @Test
    fun `log with NONE exception type is logged`() {
        // given a log with no exception
        val message = "message"
        val exceptionType = LogExceptionType.NONE

        // when logging the message
        logService.log(message, Severity.INFO, exceptionType)

        // then the message is logged
        val log = fakeLogWriter.logEvents.single()
        assertEquals(message, log.message)
    }

    @Test
    fun `get error logs count returns the correct number`() {
        // when logging some error messages
        repeat(5) {
            logService.log("error!", Severity.ERROR, LogExceptionType.NONE)
        }

        // then the correct number of error logs is returned
        assertEquals(5, logService.getErrorLogsCount())
    }

    @Test
    fun `stacktrace elements are truncated`() {
        // given a stacktrace with more than 200 elements
        val stackTrace = Array(201) { StackTraceElement("TestClass", "testMethod", "testFile", it) }

        // when logging a message with the stacktrace
        logService.log("message", Severity.INFO, LogExceptionType.HANDLED, stackTraceElements = stackTrace)

        // then the stacktrace is truncated
        val log = fakeLogWriter.logEvents.single()
        val truncatedStacktraceString = log.schemaType.attributes()[ExceptionAttributes.EXCEPTION_STACKTRACE.key] ?: ""

        // serialize back to Array<StackTraceElement> to get the size
        val truncatedStacktrace = embraceSerializer.fromJson(truncatedStacktraceString, List::class.java)
        assertEquals(200, truncatedStacktrace.size)
    }

    @Test
    fun `exception is logged`() {
        // given an exception
        val message = "Oh no"
        val exception = NullPointerException("Oh no!")

        // when logging it
        logService.log(
            message = message,
            severity = Severity.WARNING,
            logExceptionType = LogExceptionType.HANDLED,
            stackTraceElements = exception.stackTrace,
            exceptionName = exception.javaClass.simpleName,
            exceptionMessage = exception.message,
        )

        // then the exception is correctly logged
        val log = fakeLogWriter.logEvents.single()
        assertEquals(message, log.message)
        assertEquals(io.opentelemetry.api.logs.Severity.WARN, log.severity)
        val attributes = log.schemaType.attributes()
        Assert.assertNotNull(attributes[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertEquals(LogExceptionType.HANDLED.value, attributes[embExceptionHandling.name])
        assertEquals(exception.javaClass.simpleName, attributes[ExceptionAttributes.EXCEPTION_TYPE.key])
        assertEquals(exception.message, attributes[ExceptionAttributes.EXCEPTION_MESSAGE.key])
        log.assertIsType(EmbType.System.Exception)
    }

    @Test
    fun `flutter exception is logged`() {
        // given a flutter exception
        val flutterMessage = "Dart error"
        val flutterContext = "Flutter context"
        val flutterLibrary = "Flutter library"
        val flutterException = NullPointerException("Something broke on Flutter")
        fakeConfigService = FakeConfigService(appFramework = AppFramework.FLUTTER)
        logService = createEmbraceLogService()

        // when logging it
        logService.log(
            message = flutterMessage,
            severity = Severity.ERROR,
            logExceptionType = LogExceptionType.HANDLED,
            stackTraceElements = flutterException.stackTrace,
            exceptionName = flutterException.javaClass.simpleName,
            exceptionMessage = flutterException.message,
            context = flutterContext,
            library = flutterLibrary
        )

        // then the exception is correctly logged
        val log = fakeLogWriter.logEvents.single()
        assertEquals(flutterMessage, log.message)
        assertEquals(io.opentelemetry.api.logs.Severity.ERROR, log.severity)
        val attributes = log.schemaType.attributes()
        Assert.assertNotNull(attributes[LogIncubatingAttributes.LOG_RECORD_UID.key])
        assertEquals(LogExceptionType.HANDLED.value, attributes[embExceptionHandling.name])
        assertEquals(flutterException.javaClass.simpleName, attributes[ExceptionAttributes.EXCEPTION_TYPE.key])
        assertEquals(flutterException.message, attributes[ExceptionAttributes.EXCEPTION_MESSAGE.key])
        assertEquals(flutterContext, attributes[EmbType.System.FlutterException.embFlutterExceptionContext.name])
        assertEquals(flutterLibrary, attributes[EmbType.System.FlutterException.embFlutterExceptionLibrary.name])
        log.assertIsType(EmbType.System.FlutterException)
    }
}
