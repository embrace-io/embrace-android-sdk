package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.behavior.FakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeRedactionConfig
import io.embrace.android.embracesdk.fakes.createSessionBehavior
import io.embrace.android.embracesdk.internal.arch.schema.toSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
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
    private lateinit var payloadStore: FakePayloadStore

    @Before
    fun setUp() {
        fakeConfigService = FakeConfigService(
            sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
                FakeInstrumentedConfig(redaction = FakeRedactionConfig(sensitiveKeys = listOf("password"))),
            )
        )
        fakeSessionPropertiesService = FakeSessionPropertiesService()
        fakeLogWriter = FakeLogWriter()
        payloadStore = FakePayloadStore()
        logService = createEmbraceLogService()
    }

    private fun createEmbraceLogService() = EmbraceLogService(
        logWriter = fakeLogWriter,
        configService = fakeConfigService,
        sessionPropertiesService = fakeSessionPropertiesService,
        payloadStore = payloadStore,
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
        assertEquals("someValue", attributes["someProperty".toSessionPropertyAttributeName()])
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
                remoteCfg = RemoteConfig(
                    sessionConfig = SessionRemoteConfig(
                        isEnabled = true,
                        sessionComponents = emptySet() // empty set will gate everything
                    )
                )
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
    fun `log with attachment`() {
        val bytes = ByteArray(2)
        val msg = "message"
        logService.log(
            message = msg,
            severity = Severity.INFO,
            logExceptionType = LogExceptionType.NONE,
            logAttachment = Attachment.EmbraceHosted(bytes) { true },
        )

        // then the sensitive key is redacted
        val log = fakeLogWriter.logEvents.single()
        assertEquals(msg, log.message)
        val attachment = payloadStore.storedAttachments.single()
        assertEquals(bytes, attachment.data.second)
    }
}
