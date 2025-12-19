package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.attrs.embStateDroppedByInstrumentation
import io.embrace.android.embracesdk.internal.arch.attrs.embStateInitialValue
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface.Companion.LIFECYCLE_EVENT_GAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class StateFeatureTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    val stateEnabledInstrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(
            stateCaptureEnabled = true,
            bgActivityCapture = false
        )
    )

    @Test
    fun `state feature off`() {
        var throwable: Throwable? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = false
                )
            ),
            testCaseAction = {
                try {
                    findDataSource<TestStateDataSource>()
                } catch (e: IllegalStateException) {
                    throwable = e
                }
                recordSession {}
            },
            assertAction = {
                val message = getSingleSessionEnvelope()
                assertEquals(0, message.findSpansOfType(EmbType.State).size)
                assertTrue(throwable is IllegalStateException)
            }
        )
    }

    @Test
    fun `transitions during session and background activity record state spans`() {
        val stateUpdates = listOf("foo", "bar", "baz")
        val transitions: MutableList<List<Pair<Long, String>>> = mutableListOf()
        var initialStateValue = "UNKNOWN"
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    stateCaptureEnabled = true,
                    bgActivityCapture = true
                )
            ),
            testCaseAction = {
                transitions.add(executeTransitions(stateUpdates))
                recordSession {
                    transitions.add(executeTransitions(stateUpdates))
                }
                transitions.add(executeTransitions(stateUpdates))
                recordSession { }
            },
            assertAction = {
                val background = getSessionEnvelopes(2, state = AppState.BACKGROUND)
                val foreground = getSessionEnvelopes(2)

                val sessions = listOf(background.first(), foreground.first(), background.last())
                repeat(sessions.size) { i ->
                    val stateSpan = checkNotNull(sessions[i].getStateSpan("emb-state-test"))
                    with(checkNotNull(stateSpan.events)) {
                        repeat(size) { j ->
                            val event = transitions[i][j]
                            this[j].assertStateTransition(event.first, event.second)
                        }
                    }
                    val sessionSpan = checkNotNull(sessions[i].getSessionSpan())
                    assertEquals(sessionSpan.startTimeNanos, stateSpan.startTimeNanos)
                    assertEquals(sessionSpan.endTimeNanos, stateSpan.endTimeNanos)
                    stateSpan.attributes?.hasEmbraceAttributeValue(embStateInitialValue, initialStateValue)
                    initialStateValue = stateUpdates[i]
                }
            }
        )
    }

    @Test
    fun `max transitions`() {
        val stateUpdates = listOf("foo", "bar", "baz", "bar", "baz")
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            instrumentedConfig = stateEnabledInstrumentedConfig,
            testCaseAction = {
                recordSession {
                    transitions = executeTransitions(stateUpdates)
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test"))
                with(checkNotNull(stateSpan.events)) {
                    assertEquals(4, size)
                    repeat(size) { i ->
                        this[i].assertStateTransition(transitions[i].first, transitions[i].second)
                    }
                }
            }
        )
    }

    @Test
    fun `duplicate value updates`() {
        val stateUpdates = listOf("foo", "foo")
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            instrumentedConfig = stateEnabledInstrumentedConfig,
            testCaseAction = {
                recordSession {
                    transitions = executeTransitions(stateUpdates)
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test"))
                with(checkNotNull(stateSpan.events).single()) {
                    assertStateTransition(
                        timestampMs = transitions[0].first,
                        newStateValue = transitions[0].second
                    )
                }

                stateSpan.hasEmbraceAttributeValue(embStateDroppedByInstrumentation, 1)
            }
        )
    }

    @Test
    fun `transitions dropped by instrumentation are recorded`() {
        val stateUpdates = listOf("foo", "bar", "baz")
        val transitionDrops = 2
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            instrumentedConfig = stateEnabledInstrumentedConfig,
            testCaseAction = {
                recordSession {
                    transitions = executeTransitions(stateUpdates, transitionDrops)
                }
            },
            assertAction = {
                val sessionPayload = getSingleSessionEnvelope()
                val sessionSpan = checkNotNull(sessionPayload.getSessionSpan())
                val stateSpan = checkNotNull(sessionPayload.getStateSpan("emb-state-test"))
                with(checkNotNull(stateSpan.events)) {
                    assertEquals(stateUpdates.size, size)
                    repeat(size) { i ->
                        this[i].assertStateTransition(
                            timestampMs = transitions[i].first,
                            newStateValue = transitions[i].second,
                            droppedByInstrumentation = transitionDrops
                        )
                    }
                }
                assertEquals(
                    checkNotNull(sessionSpan.startTimeNanos).nanosToMillis() + LIFECYCLE_EVENT_GAP,
                    checkNotNull(stateSpan.startTimeNanos).nanosToMillis()
                )
                assertEquals(sessionSpan.endTimeNanos, stateSpan.endTimeNanos)
            }
        )
    }

    @Test
    fun `unrecorded transitions accumulates after max transitions reached`() {
        val stateUpdates = listOf("foo", "bar", "baz", "bar", "bar")
        val transitionDrops = 2
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            instrumentedConfig = stateEnabledInstrumentedConfig,
            testCaseAction = {
                recordSession {
                    transitions = executeTransitions(stateUpdates, transitionDrops)
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test"))
                with(checkNotNull(stateSpan.events)) {
                    assertEquals(4, size)
                    repeat(size) { i ->
                        this[i].assertStateTransition(
                            timestampMs = transitions[i].first,
                            newStateValue = transitions[i].second,
                            droppedByInstrumentation = transitionDrops
                        )
                    }
                }
                stateSpan.hasEmbraceAttributeValue(embStateDroppedByInstrumentation, 3)
            }
        )
    }

    @Test
    fun `transitions outside of sessions not recorded explicitly`() {
        val stateUpdates = listOf("foo", "bar")
        val transitions: MutableList<List<Pair<Long, String>>> = mutableListOf()
        testRule.runTest(
            instrumentedConfig = stateEnabledInstrumentedConfig,
            testCaseAction = {
                repeat(2) {
                    transitions.add(executeTransitions(listOf("baz")))
                    recordSession {
                        transitions.add(executeTransitions(stateUpdates))
                    }
                }
            },
            assertAction = {
                // Check that the first event of every session contains a dropped transition due to it occurring in the background
                val sessions = getSessionEnvelopes(2)
                repeat(sessions.size) { i ->
                    val periodWithTransition = transitions.filter { it.size == 2 }[i]
                    val stateSpan = checkNotNull(sessions[i].getStateSpan("emb-state-test"))
                    assertEquals(stateUpdates.size, stateSpan.events?.size)
                    checkNotNull(stateSpan.events).first().assertStateTransition(
                        timestampMs = periodWithTransition[0].first,
                        newStateValue = periodWithTransition[0].second,
                        notInSession = 1
                    )
                }
            }
        )
    }

    @Test
    fun `state value retained even if transition event dropped`() {
        val stateUpdates = listOf("foo", "bar")
        val transitions: MutableList<List<Pair<Long, String>>> = mutableListOf()
        testRule.runTest(
            instrumentedConfig = stateEnabledInstrumentedConfig,
            testCaseAction = {
                repeat(2) {
                    transitions.add(executeTransitions(listOf("baz")))
                    recordSession {
                        transitions.add(executeTransitions(stateUpdates))
                    }
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                repeat(sessions.size) { i ->
                    val stateSpan = checkNotNull(sessions[i].getStateSpan("emb-state-test"))
                    stateSpan.attributes?.hasEmbraceAttributeValue(embStateInitialValue, "baz")
                }
            }
        )
    }

    private fun EmbraceActionInterface.executeTransitions(
        updates: List<String>,
        transitionDrops: Int = 0,
    ): List<Pair<Long, String>> {
        val transitions: MutableList<Pair<Long, String>> = mutableListOf()
        val dataSource = findDataSource<TestStateDataSource>()
        repeat(updates.size) { i ->
            transitions.add(Pair(clock.tick(100L), updates[i]))
            dataSource.onStateChange(
                updateDetectedTimeMs = clock.now(),
                newState = updates[i],
                droppedTransitions = transitionDrops,
            )
        }
        return transitions
    }
}
