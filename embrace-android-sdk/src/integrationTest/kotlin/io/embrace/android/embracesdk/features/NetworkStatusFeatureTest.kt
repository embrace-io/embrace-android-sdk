package io.embrace.android.embracesdk.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.findSpanAttribute
import io.embrace.android.embracesdk.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.findSpansOfType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.recordSession
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
        with(testRule) {
            var startTimeMs: Long = 0
            val message = checkNotNull(harness.recordSession {
                startTimeMs = harness.overriddenClock.now()

                // look inside embrace internals as there isn't a good way to trigger this E2E
                val dataSource =
                    checkNotNull(bootstrapper.dataSourceModule.networkStatusDataSource.dataSource)
                harness.overriddenClock.tick(tickTimeMs)
                dataSource.networkStatusChange(NetworkStatus.WIFI, startTimeMs + tickTimeMs)
            })

            val spans = message.findSpansOfType(EmbType.System.NetworkStatus)
            assertEquals(1, spans.size)
            val span = spans.single()

            assertEquals("emb-network-status", span.name)
            assertEquals("sys.network_status", span.findSpanAttribute("emb.type"))
            assertEquals("wan", span.findSpanAttribute("network"))
            assertEquals(startTimeMs, span.startTimeNanos.nanosToMillis())
            assertEquals(startTimeMs + tickTimeMs, span.endTimeNanos.nanosToMillis())

            val snapshots = message.findSpanSnapshotsOfType(EmbType.System.NetworkStatus)
            assertEquals(1, snapshots.size)
            val snapshot = snapshots.single()

            assertEquals("emb-network-status", snapshot.name)
            assertEquals("sys.network_status", snapshot.findSpanAttribute("emb.type"))
            assertEquals("wifi", snapshot.findSpanAttribute("network"))
            assertEquals(startTimeMs + tickTimeMs, snapshot.startTimeNanos.nanosToMillis())
        }
    }

    @Test
    fun `initial session creates a span snapshot`() {
        with(testRule) {
            var startTimeMs: Long = 0
            val message = checkNotNull(harness.recordSession {
                startTimeMs = harness.overriddenClock.now()
            })

            val snapshots = message.findSpanSnapshotsOfType(EmbType.System.NetworkStatus)
            assertEquals(1, snapshots.size)
            val snapshot = snapshots.single()

            assertEquals("emb-network-status", snapshot.name)
            assertEquals("sys.network_status", snapshot.findSpanAttribute("emb.type"))
            assertEquals("wan", snapshot.findSpanAttribute("network"))
            assertEquals(startTimeMs, snapshot.startTimeNanos.nanosToMillis())
        }
    }
}
