package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkStatusFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `network status feature`() {
        val tickTimeMs = 3000L
        var sdkStartTimeMs: Long = 0
        var statusChangeTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                sdkStartTimeMs = clock.now()
                recordSession {
                    clock.tick(tickTimeMs)
                    statusChangeTimeMs = clock.now()
                    alterConnectivityStatus(NetworkStatus.WIFI)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val span = message.findSpanOfType(EmbType.System.NetworkStatus)
                assertEquals("emb-network-status", span.name)
                assertEquals(sdkStartTimeMs, span.startTimeNanos?.nanosToMillis())
                assertEquals(statusChangeTimeMs, span.endTimeNanos?.nanosToMillis())

                span.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        "network" to "unknown"
                    )
                )

                val snapshot = message.findSpanSnapshotOfType(EmbType.System.NetworkStatus)
                assertEquals("emb-network-status", snapshot.name)
                assertEquals(statusChangeTimeMs, snapshot.startTimeNanos?.nanosToMillis())
                snapshot.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        "network" to "wifi"
                    )
                )
            }
        )
    }

    @Test
    fun `initial session creates a span snapshot`() {
        var sdkStartTimeMs: Long = 0
        testRule.runTest(
            testCaseAction = {
                sdkStartTimeMs = clock.now()
                recordSession()
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val snapshot = message.findSpanSnapshotOfType(EmbType.System.NetworkStatus)
                assertEquals("emb-network-status", snapshot.name)
                assertEquals(sdkStartTimeMs, snapshot.startTimeNanos?.nanosToMillis())
                snapshot.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        "network" to "unknown"
                    )
                )
            }
        )
    }
}
