package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Validation of the internal API
 */
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class FlutterInternalInterfaceTest {

    @Suppress("DEPRECATION")
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        val fakeInitModule = FakeInitModule(clock = clock)
        EmbraceSetupInterface(
            appFramework = Embrace.AppFramework.FLUTTER,
            overriddenClock = clock,
            overriddenInitModule = fakeInitModule,
            overriddenWorkerThreadModule = FakeWorkerThreadModule(
                fakeInitModule = fakeInitModule,
                testWorkerName = Worker.Background.LogMessageWorker
            )
        )
    }

    @Test
    fun `flutter without values should return defaults`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertNull(res.hostedSdkVersion)
                assertNull(res.hostedPlatformVersion)
            }
        )
    }

    @Test
    fun `flutter methods work in current session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                    embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `flutter metadata already present from previous session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                    embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
                }
                recordSession()
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `setting null is ignored`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                    embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
                }

                recordSession {
                    embrace.flutterInternalInterface?.setDartVersion(null)
                    embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion(null)
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `flutter values from current session override previous values`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                    embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
                }

                recordSession {
                    embrace.flutterInternalInterface?.setDartVersion("28.9.2")
                    embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.4")
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.FLUTTER, res.appFramework)
                assertEquals("28.9.2", res.hostedPlatformVersion)
                assertEquals("1.2.4", res.hostedSdkVersion)
            }
        )
    }

    @Test
    fun `log flutter handled exception generates an OTel log`() {
        val expectedName = "Exception name"
        val expectedMessage = "Handled exception: name: message"
        val expectedStacktrace = "stacktrace"
        val expectedContext = "context"
        val expectedLibrary = "library"

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.flutterInternalInterface?.logHandledDartException(
                        expectedStacktrace,
                        expectedName,
                        expectedMessage,
                        expectedContext,
                        expectedLibrary,
                    )
                    flushLogs()
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()

                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "Dart error",
                    expectedSeverityNumber = Severity.ERROR.severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedExceptionName = expectedName,
                    expectedExceptionMessage = expectedMessage,
                    expectedEmbType = "sys.flutter_exception",
                    expectedState = "foreground",
                )
                log.attributes?.assertMatches {
                    ExceptionAttributes.EXCEPTION_STACKTRACE.key to expectedStacktrace
                    "emb.exception.context" to expectedContext
                    "emb.exception.library" to expectedLibrary
                }
            }
        )
    }

    @Test
    fun `log flutter unhandled exception generates an OTel log`() {
        val expectedName = "Exception name"
        val expectedMessage = "Handled exception: name: message"
        val expectedStacktrace = "stacktrace"
        val expectedContext = "context"
        val expectedLibrary = "library"

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.flutterInternalInterface?.logUnhandledDartException(
                        expectedStacktrace,
                        expectedName,
                        expectedMessage,
                        expectedContext,
                        expectedLibrary,
                    )
                    flushLogs()
                }
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLastLog()

                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "Dart error",
                    expectedSeverityNumber = Severity.ERROR.severityNumber,
                    expectedSeverityText = Severity.ERROR.name,
                    expectedType = LogExceptionType.UNHANDLED.value,
                    expectedExceptionName = expectedName,
                    expectedExceptionMessage = expectedMessage,
                    expectedEmbType = "sys.flutter_exception",
                    expectedState = "foreground",
                )
                log.attributes?.assertMatches {
                    ExceptionAttributes.EXCEPTION_STACKTRACE.key to expectedStacktrace
                    "emb.exception.context" to expectedContext
                    "emb.exception.library" to expectedLibrary
                }
            }
        )
    }

    private fun flushLogs() {
        val executor = (testRule.setup.overriddenWorkerThreadModule as FakeWorkerThreadModule).executor
        executor.runCurrentlyBlocked()
        val logOrchestrator = testRule.bootstrapper.logModule.logOrchestrator
        logOrchestrator.flush(false)
    }
}