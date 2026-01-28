package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.intArrayOf

/**
 * Integration test to verify that the X-EM-PAYLOAD-TYPES header is sent correctly
 * when logging different types of data.
 */
@RunWith(AndroidJUnit4::class)
internal class PayloadTypesHeaderTest: RobolectricTest() {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `batched logs of the same type send a single header type`() {
        testRule.runTest(
            testCaseAction = {
                embrace.logInfo("first message")
                embrace.logWarning("second message")
                embrace.logError("third message")
                clock.tick(2000L)
            },
            assertAction = {
                // Assert the envelope contains 3 logs
                val envelope = getSingleLogEnvelope()
                assertEquals(3, envelope.data.logs?.size)

                // Assert only 1 header has been tracked, and it contains the correct type
                val headers = getPayloadTypesHeaders()
                assertEquals(1, headers.size)
                assertEquals(EmbType.System.Log.value, headers[0])
            }
        )
    }

    @Config(sdk = [21])
    @Test
    fun `batched logs of different types send a list of header types`() {
        lateinit var logger: InternalLogger

        testRule.runTest(
            setupAction = {
                getEmbLogger().throwOnInternalError = false
                logger = getEmbLogger()
            },
            testCaseAction = {
                embrace.logInfo("some message")
                logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, RuntimeException("some internal error"))
                embrace.logWarning("uh oh!")
                clock.tick(2000L)
            },
            assertAction = {
                // Assert the envelope contains 3 logs
                val envelope = getSingleLogEnvelope()
                assertEquals(3, envelope.data.logs?.size)

                // Assert only 1 header has been tracked, and it contains the correct type
                val headers = getPayloadTypesHeaders()
                assertEquals(1, headers.size)
                assertEquals("${EmbType.System.Log.value},${EmbType.System.InternalError.value}", headers[0])
            }
        )
    }

    @Config(sdk = [21])
    @Test
    fun `flutter exceptions are sent immediately in separate envelopes`() {
        val instrumentedConfig = FakeInstrumentedConfig(
            project = FakeProjectConfig(
                appId = "abcde",
                appFramework = "flutter"
            )
        )
        lateinit var logger: InternalLogger

        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            setupAction = {
                getEmbLogger().throwOnInternalError = false
                logger = getEmbLogger()
            },
            testCaseAction = {
                embrace.logInfo("log message")
                logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, RuntimeException("some internal error"))
                embrace.logMessage(
                    "Flutter error occurred",
                    Severity.ERROR,
                    mapOf(
                        "emb.type" to "sys.flutter_exception",
                        "emb.private.send_mode" to "immediate",
                    )
                )
                clock.tick(2000L)
            },
            assertAction = {
                // Expect 2 envelopes:
                // 1. Batched logs (log message + internal error)
                // 2. Flutter exception (sent immediately)
                getLogEnvelopes(2)

                // Assert 2 headers were tracked
                val headers = getPayloadTypesHeaders()
                assertEquals(2, headers.size)

                // Check that all expected types are present
                assertEquals(
                    listOf("sys.flutter_exception", "sys.log,sys.internal"),
                    headers
                )
            }
        )
    }

    @Test
    fun `unity exceptions are sent immediately in separate envelopes`() {
        val instrumentedConfig = FakeInstrumentedConfig(
            project = FakeProjectConfig(
                appId = "abcde",
                appFramework = "unity"
            )
        )
        lateinit var logger: InternalLogger

        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            setupAction = {
                getEmbLogger().throwOnInternalError = false
                logger = getEmbLogger()
            },
            testCaseAction = {
                embrace.logInfo("log message")
                logger.trackInternalError(InternalErrorType.INTERNAL_INTERFACE_FAIL, RuntimeException("some internal error"))
                EmbraceInternalApi.unityInternalInterface.logUnhandledUnityException(
                    "UnityException",
                    "Unity error occurred",
                    "Unity stacktrace"
                )
                clock.tick(2000L)
            },
            assertAction = {
                // Expect 2 envelopes:
                // 1. Batched logs (log message + internal error)
                // 2. Unity exception (sent immediately)
                getLogEnvelopes(2)

                // Assert 2 headers were tracked
                val headers = getPayloadTypesHeaders()
                assertEquals(2, headers.size)

                // Check that all expected types are present
                assertEquals(
                    listOf("sys.exception", "sys.log,sys.internal"),
                    headers
                )
            }
        )
    }
}
