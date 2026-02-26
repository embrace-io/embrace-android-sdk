package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertOtelLogReceived
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.attrs.embSendMode
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.logs.LogExceptionType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.logging.model.SeverityNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation of the internal API
 */
@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class UnityInternalInterfaceTest {

    private val instrumentedConfig = FakeInstrumentedConfig(
        project = FakeProjectConfig(
            appId = "abcde",
            appFramework = "unity"
        )
    )

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `unity without values should return defaults`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertNull(res.hostedSdkVersion)
                assertNull(res.hostedPlatformVersion)
            }
        )
    }

    @Test
    fun `unity methods work in current session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.unityInternalInterface.setUnityMetaData(
                        "28.9.1",
                        "unity build id",
                        "1.2.3"
                    )
                }
            },
            assertAction = {
                val session = getSingleSessionEnvelope()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
                assertEquals("unity build id", res.unityBuildId)
            }
        )
    }

    @Test
    fun `unity metadata already present from previous session`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.unityInternalInterface.setUnityMetaData(
                        "28.9.1",
                        "unity build id",
                        "1.2.3"
                    )
                }
                recordSession()
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertEquals("28.9.1", res.hostedPlatformVersion)
                assertEquals("1.2.3", res.hostedSdkVersion)
                assertEquals("unity build id", res.unityBuildId)
            }
        )
    }

    @Test
    fun `unity values from current session override previous values`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.unityInternalInterface.setUnityMetaData(
                        "28.9.1",
                        "unity build id",
                        "1.2.3"
                    )
                }

                recordSession {
                    EmbraceInternalApi.unityInternalInterface.setUnityMetaData(
                        "28.9.2",
                        "new unity build id",
                        "1.2.4"
                    )
                }
            },
            assertAction = {
                val session = getSessionEnvelopes(2).last()
                val res = checkNotNull(session.resource)
                assertEquals(AppFramework.UNITY, res.appFramework)
                assertEquals("28.9.2", res.hostedPlatformVersion)
                assertEquals("1.2.4", res.hostedSdkVersion)
                assertEquals("new unity build id", res.unityBuildId)
            }
        )
    }

    @Test
    fun `log unity handled exception generates an OTel log`() {
        val expectedName = "Exception name"
        val expectedMessage = "Handled exception: name: message"
        val expectedStacktrace = "stacktrace"
        var sessionStartTimeMs: Long = 0

        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                sessionStartTimeMs = recordSession {
                    EmbraceInternalApi.unityInternalInterface.logHandledUnityException(
                        name = expectedName,
                        message = expectedMessage,
                        stacktrace = expectedStacktrace,
                    )
                }.actionTimeMs
            },
            assertAction = {
                val singleLogEnvelope = getSingleLogEnvelope()
                val log = singleLogEnvelope.getLogOfType(EmbType.System.Exception)

                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "Unity exception",
                    expectedSeverityNumber = SeverityNumber.ERROR,
                    expectedTimeMs = sessionStartTimeMs,
                    expectedType = LogExceptionType.HANDLED.value,
                    expectedExceptionName = expectedName,
                    expectedExceptionMessage = expectedMessage,
                    expectedEmbType = "sys.exception",
                    expectedState = "foreground",
                )
                val sendMode = log.attributes?.findAttributeValue(embSendMode.name)
                assertEquals(SendMode.IMMEDIATE, SendMode.fromString(sendMode))
            }
        )
    }

    @Test
    fun `log unity unhandled exception generates an OTel log`() {
        val expectedName = "Exception name"
        val expectedMessage = "Handled exception: name: message"
        val expectedStacktrace = "stacktrace"
        var sessionStartTimeMs: Long = 0

        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                sessionStartTimeMs = recordSession {
                    EmbraceInternalApi.unityInternalInterface.logUnhandledUnityException(
                        name = expectedName,
                        message = expectedMessage,
                        stacktrace = expectedStacktrace,
                    )
                }.actionTimeMs
            },
            assertAction = {
                val log = getSingleLogEnvelope().getLogOfType(EmbType.System.Exception)

                assertOtelLogReceived(
                    logReceived = log,
                    expectedMessage = "Unity exception",
                    expectedSeverityNumber = SeverityNumber.ERROR,
                    expectedTimeMs = sessionStartTimeMs,
                    expectedType = LogExceptionType.UNHANDLED.value,
                    expectedExceptionName = expectedName,
                    expectedExceptionMessage = expectedMessage,
                    expectedEmbType = "sys.exception",
                    expectedState = "foreground",
                )
                val sendMode = log.attributes?.findAttributeValue(embSendMode.name)
                assertEquals(SendMode.IMMEDIATE, SendMode.fromString(sendMode))
            }
        )
    }
}
