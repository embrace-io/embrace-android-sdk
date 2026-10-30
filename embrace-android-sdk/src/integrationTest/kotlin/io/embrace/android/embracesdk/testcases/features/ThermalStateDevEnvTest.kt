package io.embrace.android.embracesdk.testcases.features

import android.os.PowerManager
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.semconv.EmbThermalStateAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.server.DataUploadForwarder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

/**
 * Sends the session of [ThermalStateFeatureTest]'s scenario, built by the real SDK, to a DevEnv's SDK ingest
 * (see [DataUploadForwarder]), so the payload can be followed through the pipeline to the dashboard's
 * timeline without an emulator. Skipped unless [DataUploadForwarder.ENV_VAR] is set, so CI never runs it.
 *
 * Prints one line, `EMBRACE_DEVENV_RESULT {json}`, with what to look for on the dashboard.
 */
@Config(shadows = [ShadowActivityManager::class])
@RunWith(RobolectricTestRunner::class)
internal class ThermalStateDevEnvTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode = OtelSdkMode.COMPAT) {
        // The rule's default clock is in 1975, which no dashboard would show. A session recorded here lasts
        // about 40s of fake time, so start it far enough back to end before now.
        EmbraceSetupInterface(fakeClock = FakeClock(currentTime = System.currentTimeMillis() - SESSION_LEAD_MS))
    }

    private var forwarder: DataUploadForwarder? = null

    @Before
    fun requireForwardUrl() {
        forwarder = DataUploadForwarder.fromEnvironment()
        assumeTrue(
            "Set ${DataUploadForwarder.ENV_VAR} to the SDK ingest's base URL to run this test.",
            forwarder != null,
        )
    }

    @Test
    fun `severe thermal state followed by none is forwarded to the DevEnv ingest`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(project = FakeProjectConfig(appId = APP_ID)),
            testCaseAction = {
                recordSession {
                    val dataSource = findDataSource<ThermalStateDataSource>()
                    dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_SEVERE)
                    clock.tick(SEVERE_DURATION_MS)
                    dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
                }
            },
            assertAction = {
                // the mock server records an upload only after forwarding it, which can take a while
                val envelope = getSessionEnvelopes(expectedSize = 1, waitTimeMs = FORWARD_WAIT_MS).single()

                val severe = envelope.findSpansOfType(EmbType.Performance.ThermalState).single()
                assertEquals(
                    PowerManager.THERMAL_STATUS_SEVERE.toString(),
                    severe.attributes?.findAttributeValue(EmbThermalStateAttributes.STATUS)
                )
                assertEquals(
                    PowerManager.THERMAL_STATUS_NONE.toString(),
                    envelope.findSpanSnapshotOfType(EmbType.Performance.ThermalState).attributes
                        ?.findAttributeValue(EmbThermalStateAttributes.STATUS)
                )

                val userSessionId = envelope.getUserSessionId()
                val sessionPartId = envelope.getSessionPartId()
                val forward = getForwardedUploads().single { it.sessionPartId == sessionPartId }
                assertTrue("Forward to ${forward.url} returned ${forward.status}", forward.isSuccessful)

                // the pipeline's session id (go's codec.Session.ID, the `session` param of /v4/app/<app>/session/detail)
                // is the session part id; the user session id groups the parts of one user session.
                val result = JSONObject()
                    .put("app_id", forward.appId)
                    .put("device_id", forward.deviceId)
                    .put("session_id", sessionPartId)
                    .put("session_part_id", sessionPartId)
                    .put("user_session_id", userSessionId)
                    .put("thermal_severe_start_ms", checkNotNull(severe.startTimeNanos).nanosToMillis())
                    .put("forward_status", forward.status)
                println("$RESULT_PREFIX $result")
            },
            forwarder = forwarder,
        )
    }

    private companion object {
        const val RESULT_PREFIX = "EMBRACE_DEVENV_RESULT"
        const val APP_ID = "sazwW"
        const val SESSION_LEAD_MS = 60_000L
        const val SEVERE_DURATION_MS = 3_000L
        const val FORWARD_WAIT_MS = 45_000
    }
}
