package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getOtelSeverity
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.capture.session.isSessionPropertyAttributeName
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.LinkedList
import java.util.Queue

@RunWith(AndroidJUnit4::class)
internal class LogFeatureTest {

    private val instrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            bgActivityCapture = true
        )
    )
    private lateinit var logTimestamps: Queue<Long>

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Before
    fun before() {
        logTimestamps = LinkedList()
    }

    @Test
    fun `log info message sent in foreground`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                logTimestamps.add(
                    recordSession {
                        embrace.logInfo("test message")
                    }.actionTimeMs
                )
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Log)
                assertOtelLogReceived(
                    logReceived = log,
                    expectedTimeMs = logTimestamps.remove(),
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.INFO).severityNumber,
                    expectedSeverityText = Severity.INFO.name,
                    expectedState = "foreground",
                )
            }
        )
    }

    @Test
    fun `log warning message sent in background`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                logTimestamps.add(clock.now())
                embrace.logWarning("test message")
                clock.tick(2000L)
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Log)
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.WARNING).severityNumber,
                    expectedSeverityText = Severity.WARNING.name,
                    expectedTimeMs = logTimestamps.remove(),
                )
            }
        )
    }

    @Test
    fun `log error message sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                logTimestamps.add(clock.now())
                embrace.logError("test message")
                clock.tick(2000L)

            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Log)
                assertOtelLogReceived(
                    log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.ERROR).severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
                    expectedTimeMs = logTimestamps.remove(),
                )
            }
        )
    }

    @Test
    fun `log messages with different severities sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logMessage(expectedMessage, severity)
                }
                clock.tick(2000L)
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                    )
                }
            }
        )
    }

    @Test
    fun `log messages with different severities and properties sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logMessage(expectedMessage, severity, customProperties)
                }
                clock.tick(2000L)
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())
                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"

                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                        expectedProperties = customProperties,
                    )
                }
            })
    }

    @Test
    fun `log exception message sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                logTimestamps.add(clock.now())
                embrace.logException(testException)
                clock.tick(2000L)
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exception)
                assertOtelLogReceived(
                    log,
                    expectedMessage = checkNotNull(testException.message),
                    expectedSeverityNumber = io.opentelemetry.api.logs.Severity.ERROR.severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
                    expectedTimeMs = logTimestamps.remove(),
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedExceptionName = testException.javaClass.simpleName,
                    expectedExceptionMessage = checkNotNull(testException.message),
                    expectedStacktrace = testException.getSafeStackTrace()?.toList(),
                    expectedEmbType = "sys.exception",
                )
            }
        )
    }

    @Test
    fun `log exception with different severities sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                logTimestamps.add(clock.now())
                embrace.logException(testException, Severity.INFO)
                clock.tick(2000L)
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exception)
                assertOtelLogReceived(
                    log,
                    expectedMessage = checkNotNull(testException.message),
                    expectedSeverityNumber = io.opentelemetry.api.logs.Severity.INFO.severityNumber,
                    expectedSeverityText = Severity.INFO.name,
                    expectedTimeMs = logTimestamps.remove(),
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedExceptionName = testException.javaClass.simpleName,
                    expectedExceptionMessage = checkNotNull(testException.message),
                    expectedStacktrace = testException.getSafeStackTrace()?.toList(),
                    expectedEmbType = "sys.exception",
                )
            }
        )
    }

    @Test
    fun `log exception with different severities and properties sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    embrace.logException(
                        testException, severity,
                        customProperties
                    )
                }
                clock.tick(2000L)
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = checkNotNull(testException.message),
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedExceptionName = testException.javaClass.simpleName,
                        expectedExceptionMessage = checkNotNull(testException.message),
                        expectedStacktrace = testException.getSafeStackTrace()?.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            }
        )
    }

    @Test
    fun `log exception with different severities, properties, and custom message sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logException(testException, severity, customProperties, expectedMessage)
                }
                clock.tick(2000L)
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedExceptionName = testException.javaClass.simpleName,
                        expectedExceptionMessage = checkNotNull(testException.message),
                        expectedStacktrace = testException.getSafeStackTrace()?.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            }
        )
    }

    @Test
    fun `log custom stacktrace message sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                logTimestamps.add(clock.now())
                embrace.logCustomStacktrace(stacktrace)
                clock.tick(2000L)
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exception)
                assertOtelLogReceived(
                    log,
                    expectedMessage = "",
                    expectedSeverityNumber = getOtelSeverity(Severity.ERROR).severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
                    expectedTimeMs = logTimestamps.remove(),
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedStacktrace = stacktrace.toList(),
                    expectedEmbType = "sys.exception",
                )
            }
        )
    }

    @Test
    fun `log custom stacktrace with different severities sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    embrace.logCustomStacktrace(stacktrace, severity)
                }
                clock.tick(2000L)
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = "",
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedEmbType = "sys.exception",
                    )
                }
            }
        )
    }

    @Test
    fun `log custom stacktrace with different severities and properties sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    embrace.logCustomStacktrace(stacktrace, severity, customProperties)
                }
                clock.tick(2000L)
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = "",
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            }
        )
    }

    @Test
    fun `log custom stacktrace with different severities, properties, and custom message sent`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                Severity.values().forEach { severity ->
                    logTimestamps.add(clock.now())
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logCustomStacktrace(
                        stacktrace,
                        severity,
                        customProperties,
                        expectedMessage
                    )
                }
                clock.tick(2000L)
            },
            assertAction = {
                val envelope = getSingleLogEnvelope()
                val logs = groupLogsBySeverity(envelope)

                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedTimeMs = logTimestamps.remove(),
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            }
        )
    }

    @Test
    fun `default maximum number of session and log properties are recorded in log`() {
        val props = buildMap {
            repeat(50) {
                set("prop$it", "val")
            }
        }
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    repeat(20) {
                        embrace.addSessionProperty("session-prop$it", "val", true)
                    }
                    embrace.logMessage("test", Severity.INFO, props)
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Log)
                assertEquals(50, log.attributes?.count { it.key?.startsWith("prop") == true })
            },
            otelExportAssertion = {
                val logData = awaitLogs(1) { it.severity == io.opentelemetry.api.logs.Severity.INFO }.single()
                val totalPropsCount = logData.attributes.asMap().filter {
                    it.key.key.startsWith("prop") || it.key.key.isSessionPropertyAttributeName()
                }.size

                assertEquals(60, totalPropsCount)
            }
        )
    }

    @Test
    fun `exported logs can contain maximum number of session properties`() {
        val maxCustomSessionProps = 200
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(maxSessionProperties = maxCustomSessionProps),
            testCaseAction = {
                recordSession {
                    repeat(maxCustomSessionProps + 1) {
                        embrace.addSessionProperty("session-prop$it", "val", true)
                    }
                    embrace.logMessage("test", Severity.INFO)
                }
            },
            otelExportAssertion = {
                val logData = awaitLogs(1) { it.severity == io.opentelemetry.api.logs.Severity.INFO }.single()
                val totalPropsCount = logData.attributes.asMap().filter { it.key.key.isSessionPropertyAttributeName() }.size
                assertEquals(maxCustomSessionProps, totalPropsCount)
            }
        )
    }

    private fun getEmbraceSeverity(severityNumber: Int): Severity {
        return when (severityNumber) {
            io.opentelemetry.api.logs.Severity.INFO.severityNumber -> Severity.INFO
            io.opentelemetry.api.logs.Severity.WARN.severityNumber -> Severity.WARNING
            io.opentelemetry.api.logs.Severity.ERROR.severityNumber -> Severity.ERROR
            else -> error("Unexpected severityNumber $severityNumber")
        }
    }

    private fun groupLogsBySeverity(envelope: Envelope<LogPayload>) =
        checkNotNull(envelope.data.logs?.associateBy {
            getEmbraceSeverity(checkNotNull(it.severityNumber))
        })

    companion object {
        private val testException = IllegalArgumentException("nooooooo")
        private val customProperties: Map<String, Any> =
            linkedMapOf(Pair("first", 1), Pair("second", "two"), Pair("third", true))
        private val stacktrace = Thread.currentThread().stackTrace
    }
}
