package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        var sessionEndTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                sdkStartTimeMs = clock.now()
                recordSession {
                    clock.tick(tickTimeMs)
                    statusChangeTimeMs = clock.now()
                    val dataSource = findDataSource<NetworkStatusDataSource>()
                    dataSource.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI)
                }
                sessionEndTimeMs = clock.now()
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

                val stateSpan = message.findSpanOfType(EmbType.Performance.State)
                assertEquals("emb-state-network-connectivity", stateSpan.name)
                assertEquals(sdkStartTimeMs, stateSpan.startTimeNanos?.nanosToMillis())
                assertEquals(sessionEndTimeMs, stateSpan.endTimeNanos?.nanosToMillis())
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

    @Test
    fun `network state feature`() {
        val tickTimeMs = 3000L

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val dataSource = findDataSource<NetworkStatusDataSource>()
                    clock.tick(tickTimeMs)
                    dataSource.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI)
                    clock.tick(50000L)
                    dataSource.onNetworkConnectivityStatusChanged(NetworkStatus.NOT_REACHABLE)
                    clock.tick(9000L)
                    dataSource.onNetworkConnectivityStatusChanged(NetworkStatus.WAN)
                    clock.tick(500L)
                }
            },
            assertAction = {
                val stateSpan = getSingleSessionEnvelope().findSpanOfType(EmbType.Performance.State)
                assertNotNull(stateSpan)
            }
        )
    }
}
