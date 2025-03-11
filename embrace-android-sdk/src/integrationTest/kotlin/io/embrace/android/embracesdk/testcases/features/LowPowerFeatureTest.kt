package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LowPowerFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `low power feature`() {
        val tickTimeMs = 3000L
        var startTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                startTimeMs = recordSession {
                    // look inside embrace internals as there isn't a good way to trigger this E2E
                    alterPowerSaveMode(true)
                    clock.tick(tickTimeMs)
                    alterPowerSaveMode(false)
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
}
