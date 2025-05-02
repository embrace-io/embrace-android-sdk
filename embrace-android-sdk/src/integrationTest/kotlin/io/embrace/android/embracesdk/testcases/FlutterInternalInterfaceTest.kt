package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.testframework.assertions.getLogOfType
import io.embrace.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@RunWith(AndroidJUnit4::class)
internal class FlutterInternalInterfaceTest {

    private val instrumentedConfig = FakeInstrumentedConfig(
        project = FakeProjectConfig(
            appId = "abcde",
            appFramework = "flutter"
        )
    )

    private var sessionStartTimeMs: Long = 0L

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Before
    fun before() {
        sessionStartTimeMs = 0L
    }

    @Test
    fun `flutter without values should return defaults`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
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
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
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
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
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
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
                }

                recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setDartVersion(null)
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setEmbraceFlutterSdkVersion(null)
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
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setDartVersion("28.9.1")
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.3")
                }

                recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setDartVersion("28.9.2")
                    EmbraceInternalApi.getInstance().flutterInternalInterface.setEmbraceFlutterSdkVersion("1.2.4")
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
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                sessionStartTimeMs = recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.logHandledDartException(
                        expectedStacktrace,
                        expectedName,
                        expectedMessage,
                        expectedContext,
                        expectedLibrary,
                    )
                }.actionTimeMs
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.FlutterException)

                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "Dart error",
                    expectedSeverityNumber = SeverityNumber.ERROR,
                    expectedTimeMs = sessionStartTimeMs,
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedExceptionName = expectedName,
                    expectedExceptionMessage = expectedMessage,
                    expectedEmbType = "sys.flutter_exception",
                    expectedState = "foreground",
                )
                log.attributes?.assertMatches(
                    mapOf(
                        ExceptionAttributes.EXCEPTION_STACKTRACE.key to expectedStacktrace,
                        "emb.exception.context" to expectedContext,
                        "emb.exception.library" to expectedLibrary,
                    )
                )
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
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                sessionStartTimeMs = recordSession {
                    EmbraceInternalApi.getInstance().flutterInternalInterface.logUnhandledDartException(
                        expectedStacktrace,
                        expectedName,
                        expectedMessage,
                        expectedContext,
                        expectedLibrary,
                    )
                }.actionTimeMs
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.FlutterException)

                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "Dart error",
                    expectedSeverityNumber = SeverityNumber.ERROR,
                    expectedTimeMs = sessionStartTimeMs,
                    expectedType = LogExceptionType.UNHANDLED.value,
                    expectedExceptionName = expectedName,
                    expectedExceptionMessage = expectedMessage,
                    expectedEmbType = "sys.flutter_exception",
                    expectedState = "foreground",
                )
                log.attributes?.assertMatches(
                    mapOf(
                        ExceptionAttributes.EXCEPTION_STACKTRACE.key to expectedStacktrace,
                        "emb.exception.context" to expectedContext,
                        "emb.exception.library" to expectedLibrary,
                    )
                )
            }
        )
    }
}
