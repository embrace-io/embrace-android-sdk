package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.behavior.FakeLogMessageBehavior
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeRedactionConfig
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(IncubatingApi::class)
internal class EmbraceLogServiceTest {

    private lateinit var logService: LogServiceImpl
    private lateinit var destination: FakeTelemetryDestination
    private lateinit var fakeSessionPropertiesService: FakeSessionPropertiesService
    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var logLimitingService: LogLimitingService
    private lateinit var payloadStore: FakePayloadStore
    private lateinit var fakeTelemetryService: FakeTelemetryService

    @Before
    fun setUp() {
        fakeConfigService = FakeConfigService(
            sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
                FakeInstrumentedConfig(redaction = FakeRedactionConfig(sensitiveKeys = listOf("password"))),
            )
        )
        fakeSessionPropertiesService = FakeSessionPropertiesService()
        destination = FakeTelemetryDestination()
        payloadStore = FakePayloadStore()
        fakeTelemetryService = FakeTelemetryService()
        logLimitingService = LogLimitingServiceImpl(fakeConfigService)
        logService = createEmbraceLogService()
    }

    private fun createEmbraceLogService() = LogServiceImpl(
        destination = destination,
        configService = fakeConfigService,
        logLimitingService = logLimitingService,
        telemetryService = fakeTelemetryService,
    )

    @Test
    fun `sensitive properties are redacted`() {
        // given custom properties with a sensitive key
        val properties = mapOf("password" to "123456", "status" to "success")

        // when logging a message with those properties
        logService.log("message", LogSeverity.INFO, properties, ::Log)

        // then the sensitive key is redacted
        val log = destination.logEvents.single()
        val attributes = log.schemaType.attributes()
        assertEquals(REDACTED_LABEL, attributes["password"])
        assertEquals("success", attributes["status"])
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
        logLimitingService = LogLimitingServiceImpl(fakeConfigService)
        logService = createEmbraceLogService()

        // when logging exactly the allowed count of each type
        repeat(testLogLimit) {
            logService.log("info", LogSeverity.INFO, emptyMap(), ::Log)
            logService.log("warning!", LogSeverity.WARNING, emptyMap(), ::Log)
            logService.log("error!", LogSeverity.ERROR, emptyMap(), ::Log)
        }

        // then the logs are all logged
        assertEquals(testLogLimit * 3, destination.logEvents.size)

        // when logging one more of each type
        logService.log("info", LogSeverity.INFO, emptyMap(), ::Log)
        logService.log("warning!", LogSeverity.WARNING, emptyMap(), ::Log)
        logService.log("error!", LogSeverity.ERROR, emptyMap(), ::Log)

        // then the logs are not logged
        assertEquals(testLogLimit * 3, destination.logEvents.size)

        // and the drops are tracked
        assertEquals(3, fakeTelemetryService.appliedLimits.size)
        assertTrue(fakeTelemetryService.appliedLimits.contains("info_log" to AppliedLimitType.DROP))
        assertTrue(fakeTelemetryService.appliedLimits.contains("warning_log" to AppliedLimitType.DROP))
        assertTrue(fakeTelemetryService.appliedLimits.contains("error_log" to AppliedLimitType.DROP))
    }

    @Test
    fun `a max length smaller than 3 does not add ellipsis`() {
        // given a config with a log message limit smaller than 3
        fakeConfigService = FakeConfigService(
            logMessageBehavior = FakeLogMessageBehavior(logMessageMaximumAllowedLength = 2)
        )
        logService = createEmbraceLogService()

        // when logging a message that exceeds the limit
        logService.log("message", LogSeverity.INFO, emptyMap(), ::Log)

        // then the message is not ellipsized
        val log = destination.logEvents.single()
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
        logService.log("abcdef", LogSeverity.INFO, emptyMap(), ::Log)

        // then the message is trimmed
        val log = destination.logEvents.single()
        assertEquals("ab...", log.message)

        // and the truncation is tracked
        assertEquals(1, fakeTelemetryService.appliedLimits.size)
        assertEquals("info_log" to AppliedLimitType.TRUNCATE_STRING, fakeTelemetryService.appliedLimits.first())
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
        logService.log("abcdef", LogSeverity.INFO, emptyMap(), ::Log)

        // then the message is not trimmed
        val log = destination.logEvents.single()
        assertEquals("abcdef", log.message)

        // when logging a message that exceeds the Unity maximum allowed length
        val unityMaxAllowedLength = 16384
        logService.log(
            "a".repeat(unityMaxAllowedLength + 1),
            LogSeverity.INFO,
            emptyMap(),
            ::Log
        )

        // then the message is trimmed
        val anotherLog = destination.logEvents[1]
        assertEquals("a".repeat(unityMaxAllowedLength - 3) + "...", anotherLog.message)
    }

    @Test
    fun `log with NONE exception type is logged`() {
        // given a log with no exception
        val message = "message"

        // when logging the message
        logService.log(message, LogSeverity.INFO, emptyMap(), ::Log)

        // then the message is logged
        val log = destination.logEvents.single()
        assertEquals(message, log.message)
    }

    @Test
    fun `get error logs count returns the correct number`() {
        // when logging some error messages
        repeat(5) {
            logService.log("error!", LogSeverity.ERROR, emptyMap(), ::Log)
        }

        // then the correct number of error logs is returned
        assertEquals(5, logLimitingService.getCount(LogSeverity.ERROR))
    }

    @Test
    fun `log properties truncated properly`() {
        logService.log(
            message = "message",
            severity = LogSeverity.INFO,
            attributes = tooBigProperties,
            schemaProvider = ::Log,
        )

        // then the message is not ellipsized
        val logProps = destination.logEvents.single().schemaType.attributes().filter { it.key.startsWith("test") }
        assertEquals(truncatedProps, logProps)

        // and the truncations are tracked
        assertTrue(fakeTelemetryService.appliedLimits.contains("info_log" to AppliedLimitType.TRUNCATE_ATTRIBUTES))
        assertTrue(fakeTelemetryService.appliedLimits.contains("log_attribute_key" to AppliedLimitType.TRUNCATE_STRING))
        assertTrue(
            fakeTelemetryService.appliedLimits.contains("log_attribute_value" to AppliedLimitType.TRUNCATE_STRING)
        )
    }

    @Test
    fun `unserializable log values turned into error string`() {
        logService.log(
            message = "message",
            severity = LogSeverity.INFO,
            attributes = mapOf("badvalue" to UnSerializableClass()),
            schemaProvider = ::Log,
        )
        assertEquals("not serializable", destination.logEvents.single().schemaType.attributes()["badvalue"])
    }

    @Test
    fun `log properties unchanged if embrace not in use`() {
        fakeConfigService = FakeConfigService(onlyUsingOtelExporters = true)
        logService = createEmbraceLogService()
        logService.log(
            message = "message",
            severity = LogSeverity.INFO,
            attributes = tooBigProperties,
            schemaProvider = ::Log,
        )

        // then the message is not ellipsized
        val logProps = destination.logEvents.single().schemaType.attributes().filter { it.key.startsWith("test") }
        assertEquals(tooBigProperties, logProps)
    }

    private class UnSerializableClass

    companion object {
        private val twoHundredXs = "x".repeat(200)
        private val twoThousandXs = twoHundredXs.repeat(10)

        val tooBigProperties = mutableMapOf<String, String>().apply {
            repeat(150) {
                this["test$it$twoHundredXs"] = twoThousandXs
            }
        }

        val truncatedProps = mutableMapOf<String, String>().apply {
            val expectedValue = twoThousandXs.take(1021) + "..."
            repeat(100) {
                val key = "test$it$twoHundredXs".take(125) + "..."
                this[key] = expectedValue
            }
        }
    }
}
