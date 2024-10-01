package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.internal.worker.Worker
import io.opentelemetry.api.logs.Severity
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
        IntegrationTestRule.Harness(
            overriddenClock = clock,
            overriddenInitModule = fakeInitModule,
            overriddenWorkerThreadModule = FakeWorkerThreadModule(fakeInitModule = fakeInitModule, testWorkerName = Worker.Background.LogMessageWorker)
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
                val log = checkNotNull(getSentLogPayloads(1).getLastLog())
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(io.embrace.android.embracesdk.Severity.INFO).severityNumber,
                    expectedSeverityText = io.embrace.android.embracesdk.Severity.INFO.name,
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
                val log = checkNotNull(getSentLogPayloads(1).getLastLog())
                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(io.embrace.android.embracesdk.Severity.WARNING).severityNumber,
                    expectedSeverityText = io.embrace.android.embracesdk.Severity.WARNING.name
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
                val log = getSentLogPayloads(1).getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = "test message",
                    expectedSeverityNumber = getOtelSeverity(io.embrace.android.embracesdk.Severity.ERROR).severityNumber,
                    expectedSeverityText = io.embrace.android.embracesdk.Severity.ERROR.name
                )
            }
        )
    }

    @Test
    fun `log messages with different severities sent`() {
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            val expectedMessage = "test message ${severity.name}"

            testRule.runTest(
                testCaseAction = {
                    embrace.logMessage(expectedMessage, severity)
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name
                    )
                }
            )
        }
    }

    @Test
    fun `log messages with different severities and properties sent`() {
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            val expectedMessage = "test message ${severity.name}"
            testRule.runTest(
                testCaseAction = {
                    embrace.logMessage(expectedMessage, severity, customProperties)
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedProperties = customProperties
                    )
                }
            )
        }
    }

    @Test
    fun `log exception message sent`() {
        testRule.runTest(
            testCaseAction = {
                embrace.logException(testException)
                flushLogs()
            },
            assertAction = {
                val log = getSentLogPayloads(1).getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = checkNotNull(testException.message),
                    expectedSeverityNumber = Severity.ERROR.severityNumber,
                    expectedSeverityText = io.embrace.android.embracesdk.Severity.ERROR.name,
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
                embrace.logException(testException, io.embrace.android.embracesdk.Severity.INFO)
                flushLogs()
            },
            assertAction = {
                val log = getSentLogPayloads(1).getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = checkNotNull(testException.message),
                    expectedSeverityNumber = Severity.INFO.severityNumber,
                    expectedSeverityText = io.embrace.android.embracesdk.Severity.INFO.name,
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
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            testRule.runTest(
                testCaseAction = {
                    embrace.logException(
                        testException, severity,
                        customProperties
                    )
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
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
            )
        }
    }

    @Test
    fun `log exception with different severities, properties, and custom message sent`() {
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            val expectedMessage = "test message ${severity.name}"

            testRule.runTest(
                testCaseAction = {
                    embrace.logException(testException, severity, customProperties, expectedMessage)
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
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
            )
        }
    }

    @Test
    fun `log custom stacktrace message sent`() {
        testRule.runTest(
            testCaseAction = {
                embrace.logCustomStacktrace(stacktrace)
                flushLogs()
            },
            assertAction = {
                val log = getSentLogPayloads(1).getLastLog()
                assertOtelLogReceived(
                    log,
                    expectedMessage = "",
                    expectedSeverityNumber = getOtelSeverity(io.embrace.android.embracesdk.Severity.ERROR).severityNumber,
                    expectedSeverityText = io.embrace.android.embracesdk.Severity.ERROR.name,
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedStacktrace = stacktrace.toList(),
                    expectedEmbType = "sys.exception",
                )
            }
        )
    }

    @Test
    fun `log custom stacktrace with different severities sent`() {
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            testRule.runTest(
                testCaseAction = {
                    embrace.logCustomStacktrace(stacktrace, severity)
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
                        expectedMessage = "",
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedEmbType = "sys.exception",
                    )
                }
            )
        }
    }

    @Test
    fun `log custom stacktrace with different severities and properties sent`() {
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            testRule.runTest(
                testCaseAction = {
                    embrace.logCustomStacktrace(stacktrace, severity, customProperties)
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
                        expectedMessage = "",
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            )
        }
    }

    @Test
    fun `log custom stacktrace with different severities, properties, and custom message sent`() {
        io.embrace.android.embracesdk.Severity.values().forEach { severity ->
            val expectedMessage = "test message ${severity.name}"
            testRule.runTest(
                testCaseAction = {
                    embrace.logCustomStacktrace(stacktrace, severity, customProperties, expectedMessage)
                    flushLogs()
                },
                assertAction = {
                    val log = getSentLogPayloads().getLastLog()
                    assertOtelLogReceived(
                        log,
                        expectedMessage = expectedMessage,
                        expectedSeverityNumber = getOtelSeverity(severity).severityNumber,
                        expectedSeverityText = severity.name,
                        expectedType = LogExceptionType.HANDLED.value,
                        expectedStacktrace = stacktrace.toList(),
                        expectedProperties = customProperties,
                        expectedEmbType = "sys.exception",
                    )
                }
            )
        }
    }

    private fun flushLogs() {
        val executor = (testRule.harness.overriddenWorkerThreadModule as FakeWorkerThreadModule).executor
        executor.runCurrentlyBlocked()
        val logOrchestrator = testRule.bootstrapper.logModule.logOrchestrator
        logOrchestrator.flush(false)
    }

    private fun getOtelSeverity(severity: io.embrace.android.embracesdk.Severity): Severity {
        return when (severity) {
            io.embrace.android.embracesdk.Severity.INFO -> Severity.INFO
            io.embrace.android.embracesdk.Severity.WARNING -> Severity.WARN
            io.embrace.android.embracesdk.Severity.ERROR -> Severity.ERROR
        }
    }

    companion object {
        private val testException = IllegalArgumentException("nooooooo")
        private val customProperties: Map<String, Any> = linkedMapOf(Pair("first", 1), Pair("second", "two"), Pair("third", true))
        private val stacktrace = Thread.currentThread().stackTrace
    }
}
