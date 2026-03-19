package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.assertions.findSpanSnapshotsOfType
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.attrs.embStateDroppedByInstrumentation
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttributeValue
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
                        "network" to "unknown"
                    )
                )

                val snapshot = message.findSpanSnapshotsOfType(EmbType.System.NetworkStatus).single()
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
                        "network" to "unknown"
                    )
                )

                val snapshot = message.findSpanSnapshotsOfType(EmbType.System.NetworkStatus).single()
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
    fun `network state feature with legacy connectivity service simulation`() {
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
                    transitions.add(Pair(clock.now(), Status.WIFI))
                    simulateConnectionTypeChange(ConnectionType.WIFI, true)
                    clock.tick(10000L)
                    transitions.add(Pair(clock.now(), Status.NOT_REACHABLE))
                    simulateConnectionTypeChange(ConnectionType.NONE, true)
                    clock.tick(10000L)
                    transitions.add(Pair(clock.now(), Status.WAN))
                    simulateConnectionTypeChange(ConnectionType.WAN, true)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val sessionSpan = checkNotNull(message.getSessionSpan())
                val stateSpan = message.getStateSpan("emb-state-network")
                with(checkNotNull(stateSpan)) {
                    assertEquals(
                        checkNotNull(sessionSpan.startTimeNanos).nanosToMillis() + LIFECYCLE_EVENT_GAP,
                        checkNotNull(startTimeNanos).nanosToMillis()
                    )
                    assertEquals(sessionSpan.endTimeNanos, endTimeNanos)
                    assertTrue(stateSpan.hasEmbraceAttributeValue(embStateDroppedByInstrumentation, 1))
                    with(checkNotNull(events)) {
                        assertEquals(3, size)
                        assertEquals(transitions.size, size)
                        repeat(size) { i ->
                            // The first session will have a transition that is not recorded as the connectivity service updates the
                            // state when the listener is first registered
                            val notInSession = if (i == 0) {
                                1
                            } else {
                                0
                            }
                            // Only the second transition should have an event dropped
                            // The dropped event for the duplicate WAN status exists at the state span level
                            val droppedByInstrumentation = if (i == 1) {
                                1
                            } else {
                                0
                            }
                            this[i].assertStateTransition(
                                timestampMs = transitions[i].first,
                                newStateValue = transitions[i].second,
                                notInSession = notInSession,
                                droppedByInstrumentation = droppedByInstrumentation
                            )
                        }
                    }
                }
            }
        )
    }

    @Test
    fun `network state feature with network callback connectivity service simulation`() {
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
                    transitions.add(Pair(clock.now(), Status.WIFI_CONNECTING))
                    simulateConnectionTypeChange(ConnectionType.WIFI)
                    transitions.add(Pair(clock.now(), Status.WIFI))
                    clock.tick(10000L)
                    transitions.add(Pair(clock.now(), Status.NOT_REACHABLE))
                    simulateConnectionTypeChange(ConnectionType.NONE)
                    clock.tick(10000L)
                    transitions.add(Pair(clock.now(), Status.WAN_CONNECTING))
                    simulateConnectionTypeChange(ConnectionType.WAN)
                    transitions.add(Pair(clock.now(), Status.WAN))
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val sessionSpan = checkNotNull(message.getSessionSpan())
                val stateSpan = checkNotNull(message.getStateSpan("emb-state-network"))
                with(stateSpan) {
                    assertEquals(
                        checkNotNull(sessionSpan.startTimeNanos).nanosToMillis() + LIFECYCLE_EVENT_GAP,
                        checkNotNull(startTimeNanos).nanosToMillis()
                    )
                    assertEquals(sessionSpan.endTimeNanos, endTimeNanos)
                    checkNotNull(stateSpan.attributes).none { it.key == embStateDroppedByInstrumentation.name }
                    with(checkNotNull(events)) {
                        assertEquals(5, size)
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
