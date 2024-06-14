package io.embrace.android.embracesdk.testcases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.findLogAttribute
import io.embrace.android.embracesdk.getLastSentLog
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.worker.WorkerName
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
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
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock(IntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        val fakeInitModule = FakeInitModule(clock = clock)
        IntegrationTestRule.Harness(
            appFramework = Embrace.AppFramework.FLUTTER,
            overriddenClock = clock,
            overriddenInitModule = fakeInitModule,
            overriddenWorkerThreadModule = FakeWorkerThreadModule(fakeInitModule = fakeInitModule, name = WorkerName.REMOTE_LOGGING)
        )
    }

    @Before
    fun setup() {
        ApkToolsConfig.IS_NETWORK_CAPTURE_DISABLED = false
    }

    @Test
    fun `flutter without values should return defaults`() {
        with(testRule) {
            val session = harness.recordSession {

            }

            assertEquals(4, session?.appInfo?.appFramework)
            assertNull(session?.appInfo?.hostedSdkVersion)
            assertNull(session?.appInfo?.hostedPlatformVersion)
        }
    }

    @Test
    fun `flutter methods work in current session`() {
        with(testRule) {
            val session = harness.recordSession {
                embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
            }

            assertEquals(4, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
        }
    }

    @Test
    fun `flutter metadata already present from previous session`() {
        with(testRule) {
            harness.recordSession {
                embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
            }

            val session = harness.recordSession {

            }

            assertEquals(4, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
        }
    }

    @Test
    fun `setting null is ignored`() {
        with(testRule) {
            harness.recordSession {
                embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
            }

            val session = harness.recordSession {
                embrace.flutterInternalInterface?.setDartVersion(null)
                embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion(null)
            }

            assertEquals(4, session?.appInfo?.appFramework)
            assertEquals("28.9.1", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.3", checkNotNull(session?.appInfo?.hostedSdkVersion))
        }
    }

    @Test
    fun `flutter values from current session override previous values`() {
        with(testRule) {
            harness.recordSession {
                embrace.flutterInternalInterface?.setDartVersion("28.9.1")
                embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.3")
            }

            val session = harness.recordSession {
                embrace.flutterInternalInterface?.setDartVersion("28.9.2")
                embrace.flutterInternalInterface?.setEmbraceFlutterSdkVersion("1.2.4")
            }

            assertEquals(4, session?.appInfo?.appFramework)
            assertEquals("28.9.2", checkNotNull(session?.appInfo?.hostedPlatformVersion))
            assertEquals("1.2.4", checkNotNull(session?.appInfo?.hostedSdkVersion))
        }
    }

    @Test
    fun `log flutter handled exception generates an OTel log`() {
        val expectedName = "Exception name"
        val expectedMessage = "Handled exception: name: message"
        val expectedStacktrace = "stacktrace"
        val expectedContext = "context"
        val expectedLibrary = "library"
        with(testRule) {
            harness.recordSession {
                embrace.flutterInternalInterface?.logHandledDartException(
                    expectedStacktrace,
                    expectedName,
                    expectedMessage,
                    expectedContext,
                    expectedLibrary,
                )
                flushLogs()
            }
            val log = harness.getLastSentLog()

            assertOtelLogReceived(
                log,
                expectedMessage = "Dart error",
                expectedSeverityNumber = Severity.ERROR.severityNumber,
                expectedSeverityText = Severity.ERROR.name,
                expectedType = LogExceptionType.HANDLED.value,
                expectedExceptionName = expectedName,
                expectedExceptionMessage = expectedMessage,
                expectedEmbType = "sys.flutter_exception",
            )
            assertEquals(expectedStacktrace, log?.findLogAttribute(ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key))
            assertEquals(expectedContext, log?.findLogAttribute("emb.exception.context"))
            assertEquals(expectedLibrary, log?.findLogAttribute("emb.exception.library"))
        }
    }

    @Test
    fun `log flutter unhandled exception generates an OTel log`() {
        val expectedName = "Exception name"
        val expectedMessage = "Handled exception: name: message"
        val expectedStacktrace = "stacktrace"
        val expectedContext = "context"
        val expectedLibrary = "library"
        with(testRule) {
            harness.recordSession {
                embrace.flutterInternalInterface?.logUnhandledDartException(
                    expectedStacktrace,
                    expectedName,
                    expectedMessage,
                    expectedContext,
                    expectedLibrary,
                )
                flushLogs()
            }
            val log = harness.getLastSentLog()

            assertOtelLogReceived(
                log,
                expectedMessage = "Dart error",
                expectedSeverityNumber = Severity.ERROR.severityNumber,
                expectedSeverityText = Severity.ERROR.name,
                expectedType = LogExceptionType.UNHANDLED.value,
                expectedExceptionName = expectedName,
                expectedExceptionMessage = expectedMessage,
                expectedEmbType = "sys.flutter_exception",
            )
            assertEquals(expectedStacktrace, log?.findLogAttribute(ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE.key))
            assertEquals(expectedContext, log?.findLogAttribute("emb.exception.context"))
            assertEquals(expectedLibrary, log?.findLogAttribute("emb.exception.library"))
        }
    }

    private fun flushLogs() {
        val executor = (testRule.harness.overriddenWorkerThreadModule as FakeWorkerThreadModule).executor
        executor.runCurrentlyBlocked()
        val logOrchestrator = testRule.bootstrapper.customerLogModule.logOrchestrator
        logOrchestrator.flush(false)
    }
}