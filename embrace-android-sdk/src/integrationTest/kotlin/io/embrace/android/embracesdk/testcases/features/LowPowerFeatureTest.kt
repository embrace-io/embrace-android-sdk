package io.embrace.android.embracesdk.testcases.features

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager

@RunWith(AndroidJUnit4::class)
internal class LowPowerFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private lateinit var application: Application
    private lateinit var shadowPowerManager: ShadowPowerManager
    private lateinit var shadowMainLooper: ShadowLooper

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext() as Application
        shadowPowerManager = Shadows.shadowOf(application.getSystemService(Context.POWER_SERVICE) as PowerManager)
        shadowMainLooper = Shadows.shadowOf(Looper.getMainLooper())
    }

    @Test
    fun `low power feature`() {
        val tickTimeMs = 3000L
        var startTimeMs: Long = 0

        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(powerSaveCapture = true)),
            testCaseAction = {
                startTimeMs = recordSession {
                    setPowerSaveMode(true)
                    clock.tick(tickTimeMs)
                    setPowerSaveMode(false)
                }.actionTimeMs
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val span = message.findSpanOfType(EmbType.System.LowPower)
                span.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.low_power"
                    )
                )
                assertEquals("emb-device-low-power", span.name)
                assertEquals(startTimeMs, span.startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + tickTimeMs, span.endTimeNanos?.nanosToMillis())
            },
            otelExportAssertion = {
                val spans = awaitSpansWithType(1, EmbType.System.LowPower)
                assertSpansMatchGoldenFile(spans, "system-low-power-export.json")
            }
        )
    }

    @Test
    fun `power state feature`() {
        var firstTransitionTime = 0L
        var secondTransitionTime = 0L
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = true,
                    powerSaveCapture = true
                )
            ),
            testCaseAction = {
                recordSession {
                    firstTransitionTime = clock.now()
                    setPowerSaveMode(true)
                    clock.tick(10000L)
                    secondTransitionTime = clock.now()
                    setPowerSaveMode(false)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val sessionSpan = checkNotNull(message.getSessionSpan())
                val span = message.findSpansOfType(EmbType.State).single { it.name == "emb-state-power" }
                with(span) {
                    assertEquals(
                        checkNotNull(sessionSpan.startTimeNanos).nanosToMillis() + LIFECYCLE_EVENT_GAP,
                        checkNotNull(startTimeNanos).nanosToMillis()
                    )
                    assertEquals(sessionSpan.endTimeNanos, endTimeNanos)
                    with(checkNotNull(events)) {
                        assertEquals(2, size)
                        get(0).assertTransition(firstTransitionTime, SchemaType.PowerState.PowerMode.LOW)
                        get(1).assertTransition(secondTransitionTime, SchemaType.PowerState.PowerMode.NORMAL)
                    }
                }
            }
        )
    }

    private fun SpanEvent.assertTransition(timestampMs: Long, newState: SchemaType.PowerState.PowerMode) {
        assertEquals("transition", name)
        assertEquals(timestampMs.millisToNanos(), timestampNanos)
        assertEquals(newState.toString(), attributes?.single { it.key == "new_value" }?.data)
    }

    private fun setPowerSaveMode(powerSaveModeEnabled: Boolean) {
        shadowPowerManager.setIsPowerSaveMode(powerSaveModeEnabled)
        application.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowMainLooper.idle()
    }
}
