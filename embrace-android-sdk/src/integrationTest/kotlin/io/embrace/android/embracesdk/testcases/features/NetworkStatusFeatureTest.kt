package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.RobolectricTest
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.findSpanOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkStatusFeatureTest: RobolectricTest() {

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
                    simulateNetworkChange(NetworkStatus.WIFI)
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

    @Test
    fun `network state feature`() {
        val transitions: MutableList<Pair<Long, Status>> = mutableListOf()
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = true,
                    bgActivityCapture = false,
                    networkConnectivityCapture = true
                )
            ),
            testCaseAction = {
                recordSession {
                    simulateNetworkChange(NetworkStatus.WIFI)
                    transitions.add(Pair(clock.now(), Status.WIFI))
                    clock.tick(10000L)
                    simulateNetworkChange(NetworkStatus.NOT_REACHABLE)
                    transitions.add(Pair(clock.now(), Status.NOT_REACHABLE))
                    clock.tick(10000L)
                    simulateNetworkChange(NetworkStatus.WAN)
                    transitions.add(Pair(clock.now(), Status.WAN))
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val sessionSpan = checkNotNull(message.getSessionSpan())
                with(checkNotNull(message.getStateSpan("emb-state-network"))) {
                    assertEquals(
                        checkNotNull(sessionSpan.startTimeNanos).nanosToMillis() + LIFECYCLE_EVENT_GAP,
                        checkNotNull(startTimeNanos).nanosToMillis()
                    )
                    assertEquals(sessionSpan.endTimeNanos, endTimeNanos)
                    with(checkNotNull(events)) {
                        assertEquals(transitions.size, size)
                        repeat(size) { i ->
                            // The first session will have a transition that is not recorded as the connectivity service updates the
                            // state when the listener is first registered
                            val notInSession = if (i == 0) {
                                1
                            } else {
                                0
                            }
                            this[i].assertStateTransition(
                                timestampMs = transitions[i].first,
                                newStateValue = transitions[i].second,
                                notInSession = notInSession
                            )
                        }
                    }
                }
            }
        )
    }

    @Test
    fun `network state disabled by feature flag`() {
        var throwable: Throwable? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = true,
                    networkConnectivityCapture = false
                )
            ),
            testCaseAction = {
                try {
                    findDataSource<NetworkStateDataSource>()
                } catch (e: IllegalStateException) {
                    throwable = e
                }
                recordSession { }
            },
            assertAction = {
                assertNull(getSingleSessionEnvelope().getStateSpan("emb-state-network"))
                assertTrue(throwable is IllegalStateException)
            }
        )
    }
}
