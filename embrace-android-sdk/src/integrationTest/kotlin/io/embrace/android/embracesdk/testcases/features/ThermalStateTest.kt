package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.recordSession
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

@Config(sdk = [Build.VERSION_CODES.Q], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class ThermalStateFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setUp() {
        testRule.harness.overriddenConfigService.autoDataCaptureBehavior =
            FakeAutoDataCaptureBehavior(thermalStatusCaptureEnabled = true)
    }

    @Test
    fun `single thermal state change generates a snapshot`() {
        with(testRule) {
            var startTimeMs = 0L
            harness.recordSession {
                startTimeMs = harness.overriddenClock.now()

                val dataSource =
                    checkNotNull(bootstrapper.featureModule.thermalStateDataSource.dataSource)
                dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
            }

            val message = harness.getSingleSession()
            val snapshots = message.findSpanSnapshotsOfType(EmbType.Performance.ThermalState)
            assertEquals(1, snapshots.size)
            val snapshot = snapshots.single()

            val attrs = checkNotNull(snapshot.attributes)
            assertEquals("emb-thermal-state", snapshot.name)
            assertEquals("perf.thermal_state", attrs.findAttributeValue("emb.type"))
            assertEquals(
                PowerManager.THERMAL_STATUS_NONE.toString(),
                attrs.findAttributeValue("status")
            )
            assertEquals(startTimeMs, snapshot.startTimeNanos?.nanosToMillis())
        }
    }

    @Test
    fun `multiple thermal state changes generate spans`() {
        val tickTimeMs = 3000L
        with(testRule) {
            var startTimeMs = 0L
            harness.recordSession {
                startTimeMs = harness.overriddenClock.now()

                val dataSource =
                    checkNotNull(bootstrapper.featureModule.thermalStateDataSource.dataSource)
                dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
                harness.overriddenClock.tick(tickTimeMs)
                dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_MODERATE)
                harness.overriddenClock.tick(tickTimeMs)
                dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
            }

            val message = harness.getSingleSession()
            val spans = message.findSpansOfType(EmbType.Performance.ThermalState)
            assertEquals(2, spans.size)

            spans.forEach {
                assertEquals("emb-thermal-state", it.name)
                assertEquals("perf.thermal_state", it.attributes?.findAttributeValue("emb.type"))
            }
            val firstSpan = spans.first()
            assertEquals(
                PowerManager.THERMAL_STATUS_CRITICAL.toString(),
                firstSpan.attributes?.findAttributeValue("status")
            )
            assertEquals(startTimeMs, firstSpan.startTimeNanos?.nanosToMillis())
            assertEquals(startTimeMs + tickTimeMs, firstSpan.endTimeNanos?.nanosToMillis())
            val secondSpan = spans.last()
            assertEquals(
                PowerManager.THERMAL_STATUS_MODERATE.toString(),
                secondSpan.attributes?.findAttributeValue("status")
            )
            assertEquals(startTimeMs + tickTimeMs, secondSpan.startTimeNanos?.nanosToMillis())
            assertEquals(startTimeMs + tickTimeMs * 2, secondSpan.endTimeNanos?.nanosToMillis())

            val snapshots = message.findSpanSnapshotsOfType(EmbType.Performance.ThermalState)
            assertEquals(1, snapshots.size)

            val snapshot = snapshots.single()
            assertEquals("emb-thermal-state", snapshot.name)
            assertEquals("perf.thermal_state", snapshot.attributes?.findAttributeValue("emb.type"))
            assertEquals(
                PowerManager.THERMAL_STATUS_NONE.toString(),
                snapshot.attributes?.findAttributeValue("status")
            )
            assertEquals(startTimeMs + tickTimeMs * 2, snapshot.startTimeNanos?.nanosToMillis())
        }
    }

}
