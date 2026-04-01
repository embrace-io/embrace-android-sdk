package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.semconv.EmbNetworkStatusAttributes
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
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
    fun `network status feature with legacy connectivity service simulation`() {
        val tickTimeMs = 3000L
        var sdkStartTimeMs: Long = 0
        var statusChangeTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                sdkStartTimeMs = clock.now()
                recordSession {
                    clock.tick(tickTimeMs)
                    statusChangeTimeMs = clock.now()
                    simulateConnectionTypeChange(ConnectionType.WIFI, true)
                    // duplicates should not result in another span being created
                    clock.tick()
                    simulateConnectionTypeChange(ConnectionType.WIFI, true)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val span = message.findSpansOfType(EmbType.System.NetworkStatus).single()
                assertEquals("emb-network-status", span.name)
                assertEquals(sdkStartTimeMs, span.startTimeNanos?.nanosToMillis())
                assertEquals(statusChangeTimeMs, span.endTimeNanos?.nanosToMillis())

                span.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        EmbNetworkStatusAttributes.NETWORK to "unknown"
                    )
                )

                val snapshot = message.findSpanSnapshotsOfType(EmbType.System.NetworkStatus).single()
                assertEquals("emb-network-status", snapshot.name)
                assertEquals(statusChangeTimeMs, snapshot.startTimeNanos?.nanosToMillis())
                snapshot.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        EmbNetworkStatusAttributes.NETWORK to "wifi"
                    )
                )
            }
        )
    }

    @Test
    fun `network status feature with network callback based connectivity service simulation`() {
        val tickTimeMs = 3000L
        var sdkStartTimeMs: Long = 0
        var statusChangeTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                sdkStartTimeMs = clock.now()
                recordSession {
                    clock.tick(tickTimeMs)
                    statusChangeTimeMs = clock.now()
                    simulateConnectionTypeChange(ConnectionType.WIFI)
                    // duplicates should not result in another span being created
                    clock.tick()
                    simulateConnectionTypeChange(ConnectionType.WIFI)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val span = message.findSpansOfType(EmbType.System.NetworkStatus).single()
                assertEquals("emb-network-status", span.name)
                assertEquals(sdkStartTimeMs, span.startTimeNanos?.nanosToMillis())
                assertEquals(statusChangeTimeMs, span.endTimeNanos?.nanosToMillis())

                span.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        EmbNetworkStatusAttributes.NETWORK to "unknown"
                    )
                )

                val snapshot = message.findSpanSnapshotsOfType(EmbType.System.NetworkStatus).single()
                assertEquals("emb-network-status", snapshot.name)
                assertEquals(statusChangeTimeMs, snapshot.startTimeNanos?.nanosToMillis())
                snapshot.attributes?.assertMatches(
                    mapOf(
                        "emb.type" to "sys.network_status",
                        EmbNetworkStatusAttributes.NETWORK to "wifi"
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
                        EmbNetworkStatusAttributes.NETWORK to "unknown"
                    )
                )
            }
        )
    }
}
