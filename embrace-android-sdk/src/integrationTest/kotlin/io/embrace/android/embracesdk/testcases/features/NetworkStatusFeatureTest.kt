package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkStatusFeatureTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Test
    fun `network status feature`() {
        val tickTimeMs = 3000L
        var startTimeMs: Long = 0

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    startTimeMs = clock.now()

                    // look inside embrace internals as there isn't a good way to trigger this E2E
                    val dataSource =
                        checkNotNull(testRule.bootstrapper.featureModule.networkStatusDataSource.dataSource)
                    clock.tick(tickTimeMs)
                    dataSource.onNetworkConnectivityStatusChanged(NetworkStatus.WIFI)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val span = message.findSpanOfType(EmbType.System.NetworkStatus)
                assertEquals("emb-network-status", span.name)
                assertEquals(startTimeMs, span.startTimeNanos?.nanosToMillis())
                assertEquals(startTimeMs + tickTimeMs, span.endTimeNanos?.nanosToMillis())

                span.attributes?.assertMatches {
                    "emb.type" to "sys.network_status"
                    "network" to "unknown"
                }

                val snapshot = message.findSpanSnapshotOfType(EmbType.System.NetworkStatus)
                assertEquals("emb-network-status", snapshot.name)
                assertEquals(startTimeMs + tickTimeMs, snapshot.startTimeNanos?.nanosToMillis())
                snapshot.attributes?.assertMatches {
                    "emb.type" to "sys.network_status"
                    "network" to "wifi"
                }
            }
        )
    }

    @Test
    fun `initial session creates a span snapshot`() {
        var startTimeMs: Long = 0
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    startTimeMs = clock.now()
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val snapshot = message.findSpanSnapshotOfType(EmbType.System.NetworkStatus)
                assertEquals("emb-network-status", snapshot.name)
                assertEquals(startTimeMs, snapshot.startTimeNanos?.nanosToMillis())
                snapshot.attributes?.assertMatches {
                    "emb.type" to "sys.network_status"
                    "network" to "unknown"
                }
            }
        )
    }
}
