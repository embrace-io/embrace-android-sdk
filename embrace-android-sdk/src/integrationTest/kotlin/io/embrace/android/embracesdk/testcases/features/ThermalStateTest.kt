package io.embrace.android.embracesdk.testcases.features

import android.os.Build
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.internal.instrumentation.thermalstate.ThermalStateDataSource
import org.junit.Assert.assertEquals
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
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `single thermal state change generates a snapshot`() {
        var startTimeMs = 0L

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    startTimeMs = clock.now()
                    val dataSource = findDataSource<ThermalStateDataSource>()
                    dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val snapshot = message.findSpanSnapshotOfType(EmbType.Performance.ThermalState)

                val attrs = checkNotNull(snapshot.attributes)
                assertEquals("emb-thermal-state", snapshot.name)
                assertEquals("perf.thermal_state", attrs.findAttributeValue("emb.type"))
                assertEquals(
                    PowerManager.THERMAL_STATUS_NONE.toString(),
                    attrs.findAttributeValue("status")
                )
                assertEquals(startTimeMs, snapshot.startTimeNanos?.nanosToMillis())
            }
        )
    }

    @Test
    fun `multiple thermal state changes generate spans`() {
        val tickTimeMs = 3000L
        var startTimeMs = 0L

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    startTimeMs = clock.now()

                    val dataSource = findDataSource<ThermalStateDataSource>()
                    dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_CRITICAL)
                    clock.tick(tickTimeMs)
                    dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_MODERATE)
                    clock.tick(tickTimeMs)
                    dataSource.handleThermalStateChange(PowerManager.THERMAL_STATUS_NONE)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val spans = message.findSpansOfType(EmbType.Performance.ThermalState)
                assertEquals(2, spans.size)

                spans.forEach {
                    assertEquals("emb-thermal-state", it.name)
                    assertEquals("perf.thermal_state", it.attributes?.findAttributeValue("emb.type"))
                }
                val firstSpan = spans.first()
                firstSpan.attributes?.assertMatches(mapOf(
                    "status" to PowerManager.THERMAL_STATUS_CRITICAL.toString()
                ))
                assertEquals(startTimeMs, firstSpan.startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + tickTimeMs, firstSpan.endTimeNanos?.nanosToMillis())
                val secondSpan = spans.last()
                secondSpan.attributes?.assertMatches(mapOf(
                    "status" to PowerManager.THERMAL_STATUS_MODERATE.toString()
                ))
                assertEquals(startTimeMs + tickTimeMs, secondSpan.startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + tickTimeMs * 2, secondSpan.endTimeNanos?.nanosToMillis())

                val snapshot = message.findSpanSnapshotOfType(EmbType.Performance.ThermalState)
                assertEquals("emb-thermal-state", snapshot.name)
                assertEquals("perf.thermal_state", snapshot.attributes?.findAttributeValue("emb.type"))
                snapshot.attributes?.assertMatches(mapOf(
                    "status" to PowerManager.THERMAL_STATUS_NONE.toString()
                ))
                assertEquals(startTimeMs + tickTimeMs * 2, snapshot.startTimeNanos?.nanosToMillis())
            }
        )
    }
}
