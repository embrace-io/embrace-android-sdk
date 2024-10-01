package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LowPowerFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `low power feature`() {
        val tickTimeMs = 3000L
        var startTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    startTimeMs = clock.now()

                    // look inside embrace internals as there isn't a good way to trigger this E2E
                    val dataSource =
                        checkNotNull(testRule.bootstrapper.featureModule.lowPowerDataSource.dataSource)
                    dataSource.onPowerSaveModeChanged(true)
                    clock.tick(tickTimeMs)
                    dataSource.onPowerSaveModeChanged(false)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val span = message.findSpanOfType(EmbType.System.LowPower)
                span.attributes?.assertMatches {
                    "emb.type" to "sys.low_power"
                }
                assertEquals("emb-device-low-power", span.name)
                assertEquals(startTimeMs, span.startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + tickTimeMs, span.endTimeNanos?.nanosToMillis())
            }
        )
    }
}
