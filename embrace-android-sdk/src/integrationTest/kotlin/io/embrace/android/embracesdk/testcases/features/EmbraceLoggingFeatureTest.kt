package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class EmbraceLoggingFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        val fakeInitModule = FakeInitModule(clock = clock)
        EmbraceSetupInterface(
            overriddenClock = clock,
            overriddenInitModule = fakeInitModule,
            overriddenWorkerThreadModule = FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorkerName = Worker.Background.LogMessageWorker
            )
        )
    }

    @Test
    fun `log info message sent in foreground`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logInfo("test message")
                    flushLogs()
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    logReceived = log,
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
            testCaseAction = {
                embrace.logWarning("test message")
                flushLogs()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.WARNING).severityNumber,
                    expectedSeverityText = Severity.WARNING.name
                )
            }
        )
    }

    @Test
    fun `log error message sent`() {
        testRule.runTest(
            testCaseAction = {
                embrace.logError("test message")
                flushLogs()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(Severity.ERROR).severityNumber,
                    expectedSeverityText = Severity.ERROR.name
                )
            }
        )
    }

    @Test
    fun `log messages with different severities sent`() {
        testRule.runTest(
            testCaseAction = {
                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logMessage(expectedMessage, severity)
                }
                flushLogs()
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name
                    )
                }
            }
        )
    }

    @Test
    fun `log messages with different severities and properties sent`() {
        testRule.runTest(
            testCaseAction = {
                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logMessage(expectedMessage, severity, customProperties)
                }
                flushLogs()
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
                        expectedProperties = customProperties
                    )
                }
            })
    }

    @Test
    fun `log exception message sent`() {
        testRule.runTest(
            testCaseAction = {
                embrace.logException(testException)
                flushLogs()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = checkNotNull(testException.message),
                    expectedSeverityNumber = io.opentelemetry.api.logs.Severity.ERROR.severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
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
            testCaseAction = {
                embrace.logException(testException, Severity.INFO)
                flushLogs()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = checkNotNull(testException.message),
                    expectedSeverityNumber = io.opentelemetry.api.logs.Severity.INFO.severityNumber,
                    expectedSeverityText = Severity.INFO.name,
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
            testCaseAction = {
                Severity.values().forEach { severity ->
                    embrace.logException(
                        testException, severity,
                        customProperties
                    )
                }
                flushLogs()
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = checkNotNull(testException.message),
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
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
            testCaseAction = {
                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logException(testException, severity, customProperties, expectedMessage)
                }
                flushLogs()
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
            testCaseAction = {
                embrace.logCustomStacktrace(stacktrace)
                flushLogs()
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = "",
                    expectedSeverityNumber = getOtelSeverity(Severity.ERROR).severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
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
            testCaseAction = {
                Severity.values().forEach { severity ->
                    embrace.logCustomStacktrace(stacktrace, severity)
                }
                flushLogs()
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = "",
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
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
            testCaseAction = {
                Severity.values().forEach { severity ->
                    embrace.logCustomStacktrace(stacktrace, severity, customProperties)
                }
                flushLogs()
            },
            assertAction = {
                val logs = groupLogsBySeverity(getSingleLogEnvelope())

                Severity.values().forEach { severity ->
                    assertOtelLogReceived(
                        logs[severity],
                        expectedMessage = "",
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
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
            testCaseAction = {
                Severity.values().forEach { severity ->
                    val expectedMessage = "test message ${severity.name}"
                    embrace.logCustomStacktrace(
                        stacktrace,
                        severity,
                        customProperties,
                        expectedMessage
                    )
                }
                flushLogs()
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
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            }
        )
    }

    private fun flushLogs() {
        val executor =
            (testRule.setup.overriddenWorkerThreadModule as FakeWorkerThreadModule).executor
        executor.runCurrentlyBlocked()
        val logOrchestrator = testRule.bootstrapper.logModule.logOrchestrator
        logOrchestrator.flush(false)
    }

    private fun getOtelSeverity(severity: Severity): io.opentelemetry.api.logs.Severity {
        return when (severity) {
            Severity.INFO -> io.opentelemetry.api.logs.Severity.INFO
            Severity.WARNING -> io.opentelemetry.api.logs.Severity.WARN
            Severity.ERROR -> io.opentelemetry.api.logs.Severity.ERROR
        }
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
