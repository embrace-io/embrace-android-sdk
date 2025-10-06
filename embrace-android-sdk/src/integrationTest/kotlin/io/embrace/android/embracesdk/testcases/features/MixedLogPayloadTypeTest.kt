package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test to demonstrate that X-EM-TYPES header only reflects the first log's type when multiple logs of different types
 * are batched together.
 */
@RunWith(AndroidJUnit4::class)
internal class MixedLogPayloadTypeTest {

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
    fun `regular log followed by unity exception sets X-EM-TYPES to first log type`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                // Log a regular info message first
                embrace.logInfo("Regular log message")

                // Then log a Unity unhandled exception
                EmbraceInternalApi.getInstance().unityInternalInterface.logUnhandledUnityException(
                    "UnityException",
                    "Unity error occurred",
                    "at UnityEngine.GameObject.DoSomething()"
                )

                // Wait for logs to be batched and sent
                clock.tick(2000L)
            },
            assertAction = {
                // Get the single log envelope that should contain both logs
                val logs = getSingleLogEnvelope().data.logs

                // Verify we have 2 logs in the same envelope (batched together)
                assertEquals(2, logs?.size)

                // First log should be regular log (sys.log)
                val firstLog = logs?.get(0)
                val firstLogType = firstLog?.attributes?.find { it.key == "emb.type" }?.data
                assertEquals("sys.log", firstLogType)

                // Second log should be Unity exception (sys.exception)
                val secondLog = logs?.get(1)
                val secondLogType = secondLog?.attributes?.find { it.key == "emb.type" }?.data
                assertEquals("sys.exception", secondLogType)

                // The X-EM-TYPES header sent to server is the first log type
                val headers = getLogRequestHeaders().single()
                assertEquals("sys.log", headers["x-em-types"])
            }
        )
    }

    @Test
    fun `unity exception followed by regular log sets X-EM-TYPES to exception`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                // Log a Unity unhandled exception first
                EmbraceInternalApi.getInstance().unityInternalInterface.logUnhandledUnityException(
                    "UnityException",
                    "Unity error occurred",
                    "at UnityEngine.GameObject.DoSomething()"
                )

                // Then log a regular info message
                embrace.logInfo("Regular log message")

                // Wait for logs to be batched and sent
                clock.tick(2000L)
            },
            assertAction = {
                // Get the single log envelope that should contain both logs
                val logs = getSingleLogEnvelope().data.logs

                // Verify we have 2 logs in the same envelope (batched together)
                assertEquals(2, logs?.size)

                // First log should be Unity exception (sys.exception)
                val firstLog = logs?.get(0)
                val firstLogType = firstLog?.attributes?.find { it.key == "emb.type" }?.data
                assertEquals("sys.exception", firstLogType)

                // Second log should be regular log (sys.log)
                val secondLog = logs?.get(1)
                val secondLogType = secondLog?.attributes?.find { it.key == "emb.type" }?.data
                assertEquals("sys.log", secondLogType)

                // The X-EM-TYPES header sent to server is the first log type
                val headers = getLogRequestHeaders().single()
                assertEquals("sys.exception", headers["x-em-types"])
            }
        )
    }

    @Test
    fun `multiple unity exceptions batched together`() {
        testRule.runTest(
            instrumentedConfig = instrumentedConfig,
            testCaseAction = {
                // Log multiple Unity exceptions
                repeat(3) { index ->
                    EmbraceInternalApi.getInstance().unityInternalInterface.logUnhandledUnityException(
                        "UnityException$index",
                        "Unity error $index occurred",
                        "at UnityEngine.GameObject.DoSomething$index()"
                    )
                }

                // Wait for logs to be batched and sent
                clock.tick(2000L)
            },
            assertAction = {
                // Get the single log envelope that should contain all 3 exceptions
                val logs = getSingleLogEnvelope().data.logs

                // Verify we have 3 logs in the same envelope (batched together)
                assertNotNull(logs)
                assertEquals(3, logs?.size)

                // All logs should be Unity exceptions (sys.exception)
                logs?.forEach { log ->
                    val logType = log.attributes?.find { it.key == "emb.type" }?.data
                    assertEquals("sys.exception", logType)
                }

                // X-EM-TYPES should be "sys.exception"
                val headers = getLogRequestHeaders().single()
                assertEquals("sys.exception", headers["x-em-types"])
            }
        )
    }
}
