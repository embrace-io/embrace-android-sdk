package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [NetworkStateDataSource] covering the full [ConnectivityStatus]
 * space, including unvalidated networks, captive portals, and the realistic
 * transition sequences of [ConnectivityStatus].
 */
@RunWith(AndroidJUnit4::class)
internal class NetworkStateFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    private val networkStateEnabledConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            networkConnectivityCapture = true
        )
    )

    @Test
    fun `network state feature with legacy connectivity service simulation`() {
        val transitions: MutableList<Pair<Long, Status>> = mutableListOf()
        testRule.runTest(
            instrumentedConfig = networkStateEnabledConfig,
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
                            this[i].assertStateTransition(
                                timestampMs = transitions[i].first,
                                newStateValue = transitions[i].second,
                                notInSession = notInSession,
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
            instrumentedConfig = networkStateEnabledConfig,
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
                    checkNotNull(stateSpan.attributes).none { it.key == EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION }
                    with(checkNotNull(events)) {
                        assertEquals(5, size)
                        assertEquals(transitions.size, size)
                        repeat(size) { i ->
                            // The first session will have a transition that is not recorded, i.e. connectivity service initializes
                            // and sends the listener the initial state when its first registered
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

    @Test
    fun `all ConnectivityStatus types produce correct state transitions`() {
        val transitions = mutableListOf<Pair<Long, Status>>()
        testRule.runTest(
            instrumentedConfig = networkStateEnabledConfig,
            testCaseAction = {
                recordSession {
                    // fire the raw events rather than use a helper for maximum verification
                    transitions.add(Pair(clock.now(), Status.WIFI_CONNECTING))
                    simulateConnectivityChange(ConnectivityStatus.Wifi(false))

                    clock.tick(1000L)
                    transitions.add(Pair(clock.now(), Status.WIFI))
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))

                    clock.tick(1000L)
                    transitions.add(Pair(clock.now(), Status.NOT_REACHABLE))
                    simulateConnectivityChange(ConnectivityStatus.None)

                    clock.tick(1000L)
                    transitions.add(Pair(clock.now(), Status.WAN_CONNECTING))
                    simulateConnectivityChange(ConnectivityStatus.Wan(false))

                    clock.tick(1000L)
                    transitions.add(Pair(clock.now(), Status.WAN))
                    simulateConnectivityChange(ConnectivityStatus.Wan(true))

                    clock.tick(1000L)
                    transitions.add(Pair(clock.now(), Status.UNKNOWN_CONNECTING))
                    simulateConnectivityChange(ConnectivityStatus.Unknown(false))

                    clock.tick(1000L)
                    transitions.add(Pair(clock.now(), Status.UNKNOWN))
                    simulateConnectivityChange(ConnectivityStatus.Unknown(true))
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val stateSpan = checkNotNull(message.getStateSpan("emb-state-network"))
                val events = checkNotNull(stateSpan.events)
                assertEquals(transitions.size, events.size)
                transitions.forEachIndexed { i, (timestampMs, expectedStatus) ->
                    events[i].assertStateTransition(
                        timestampMs = timestampMs,
                        newStateValue = expectedStatus,
                        notInSession = if (i == 0) {
                            1
                        } else {
                            0
                        }
                    )
                }
            }
        )
    }

    @Test
    fun `captive portal wifi stays in WIFI_CONNECTING without transitioning to wifi`() {
        val transitions = mutableListOf<Pair<Long, Status>>()
        testRule.runTest(
            instrumentedConfig = networkStateEnabledConfig,
            testCaseAction = {
                recordSession {
                    // Simulate captive portal: wifi available but never validated
                    transitions.add(Pair(clock.now(), Status.WIFI_CONNECTING))
                    simulateConnectivityChange(ConnectivityStatus.Wifi(false))
                    clock.tick(10000L)

                    // Eventually validation completes — this is the only wifi transition
                    transitions.add(Pair(clock.now(), Status.WIFI))
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val stateSpan = checkNotNull(message.getStateSpan("emb-state-network"))
                val events = checkNotNull(stateSpan.events)
                assertEquals(transitions.size, events.size)
                events[0].assertStateTransition(
                    timestampMs = transitions[0].first,
                    newStateValue = Status.WIFI_CONNECTING,
                    notInSession = 1
                )
                events[1].assertStateTransition(
                    timestampMs = transitions[1].first,
                    newStateValue = Status.WIFI
                )
            }
        )
    }

    @Test
    fun `duplicate ConnectivityStatus does not create extra transition`() {
        val transitions = mutableListOf<Pair<Long, Status>>()
        testRule.runTest(
            instrumentedConfig = networkStateEnabledConfig,
            testCaseAction = {
                recordSession {
                    transitions.add(Pair(clock.now(), Status.WIFI))
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                    clock.tick(1000L)
                    // Same status again — should NOT produce another transition
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                    clock.tick(1000L)
                }
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                val stateSpan = checkNotNull(message.getStateSpan("emb-state-network"))
                val events = checkNotNull(stateSpan.events)
                assertEquals(transitions.size, events.size)
            }
        )
    }
}
