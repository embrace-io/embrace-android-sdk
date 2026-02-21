package io.embrace.android.embracesdk.testcases.features

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.powersave.PowerStateDataSource
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
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

    @Config(sdk = [21])
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

    @Config(sdk = [21])
    @Test
    fun `power state feature`() {
        val transitions: MutableList<Pair<Long, SchemaType.PowerState.PowerMode>> = mutableListOf()
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = false,
                    powerSaveCapture = true
                )
            ),
            persistedRemoteConfig = RemoteConfig(pctStateCaptureEnabledV2 = 100.0f),
            testCaseAction = {
                recordSession {
                    setPowerSaveMode(true)
                    transitions.add(Pair(clock.now(), SchemaType.PowerState.PowerMode.LOW))
                    clock.tick(10000L)
                    setPowerSaveMode(false)
                    transitions.add(Pair(clock.now(), SchemaType.PowerState.PowerMode.NORMAL))
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val sessionSpan = checkNotNull(message.getSessionSpan())
                with(checkNotNull(message.getStateSpan("emb-state-power"))) {
                    assertEquals(
                        checkNotNull(sessionSpan.startTimeNanos).nanosToMillis() + LIFECYCLE_EVENT_GAP,
                        checkNotNull(startTimeNanos).nanosToMillis()
                    )
                    assertEquals(sessionSpan.endTimeNanos, endTimeNanos)
                    with(checkNotNull(events)) {
                        assertEquals(2, size)
                        repeat(size) { i ->
                            this[i].assertStateTransition(
                                timestampMs = transitions[i].first,
                                newStateValue = transitions[i].second,
                                notInSession = if (i == 0) {
                                    1
                                } else {
                                    0
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    @Test
    fun `power state disabled by feature flag`() {
        var throwable: Throwable? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    powerSaveCapture = false
                )
            ),
            persistedRemoteConfig = RemoteConfig(pctStateCaptureEnabledV2 = 100.0f),
            testCaseAction = {
                try {
                    findDataSource<PowerStateDataSource>()
                } catch (e: IllegalStateException) {
                    throwable = e
                }
                recordSession {
                    setPowerSaveMode(true)
                }
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getStateSpan("emb-state-power"))
                assertTrue(throwable is IllegalStateException)
            }
        )
    }

    private fun setPowerSaveMode(powerSaveModeEnabled: Boolean) {
        shadowPowerManager.setIsPowerSaveMode(powerSaveModeEnabled)
        application.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowMainLooper.idle()
    }
}
