package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
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
        with(testRule) {
            var startTimeMs: Long = 0
            harness.recordSession {
                startTimeMs = harness.overriddenClock.now()

                // look inside embrace internals as there isn't a good way to trigger this E2E
                val dataSource =
                    checkNotNull(bootstrapper.featureModule.lowPowerDataSource.dataSource)
                dataSource.onPowerSaveModeChanged(true)
                harness.overriddenClock.tick(tickTimeMs)
                dataSource.onPowerSaveModeChanged(false)
            }

            val message = harness.getSingleSession()
            val spans = message.findSpansOfType(EmbType.System.LowPower)
            assertEquals(1, spans.size)
            val span = spans.single()

            val attrs = checkNotNull(span.attributes)
            assertEquals("emb-device-low-power", span.name)
            assertEquals("sys.low_power", attrs.findAttributeValue("emb.type"))
            assertEquals(startTimeMs, span.startTimeNanos?.nanosToMillis())
            assertEquals(startTimeMs + tickTimeMs, span.endTimeNanos?.nanosToMillis())
        }
    }
}
