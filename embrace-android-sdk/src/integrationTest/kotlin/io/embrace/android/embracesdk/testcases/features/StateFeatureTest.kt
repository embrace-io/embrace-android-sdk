package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertStateTransition
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.assertions.hasLinkToEmbraceSpan
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttributeValue
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.session.getStateSpan
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
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

    val stateEnabledRemoteConfig = RemoteConfig(pctStateCaptureEnabledV2 = 100.0f)
    val stateDisabledRemoteConfig = RemoteConfig(pctStateCaptureEnabledV2 = 0.0f)

    @Test
    fun `state feature on by default`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertTrue(getSingleSessionEnvelope().findSpansOfType(EmbType.State).isNotEmpty())
            }
        )
    }

    @Test
    fun `state feature off if disabled by remote config `() {
        var throwable: Throwable? = null
        testRule.runTest(
            persistedRemoteConfig = stateDisabledRemoteConfig,
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
                    bgActivityCapture = true
                )
            ),
            persistedRemoteConfig = stateEnabledRemoteConfig,
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
                    assertTrue(checkNotNull(stateSpan.attributes).hasEmbraceAttribute(PrivateSpan))
                    with(checkNotNull(stateSpan.events)) {
                        repeat(size) { j ->
                            val event = transitions[i][j]
                            this[j].assertStateTransition(event.first, event.second)
                        }
                    }
                    val sessionSpan = checkNotNull(sessions[i].getSessionSpan())
                    sessionSpan.hasLinkToEmbraceSpan(stateSpan, LinkType.State)
                    assertEquals(sessionSpan.startTimeNanos, stateSpan.startTimeNanos)
                    assertEquals(sessionSpan.endTimeNanos, stateSpan.endTimeNanos)
                    stateSpan.attributes?.hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE, initialStateValue)
                    initialStateValue = stateUpdates[i]
                }
            }
        )
    }

    @Test
    fun `max transitions`() {
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
            testCaseAction = {
                recordSession {
                    repeat(101) { i ->
                        findDataSource<TestStateDataSource>().onStateChange(
                            newState = i.toString(),
                            transitionTimeMs = clock.tick(),
                        )
                    }
                }
            },
            assertAction = {
                val stateSpan = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test"))
                assertEquals(100, checkNotNull(stateSpan.events).size)
            }
        )
    }

    @Test
    fun `duplicate value updates`() {
        val stateUpdates = listOf("foo", "foo")
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
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

                stateSpan.hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION, 1)
            }
        )
    }

    @Test
    fun `transitions dropped by instrumentation are recorded`() {
        val stateUpdates = listOf("foo", "bar", "baz")
        val transitionDrops = 2
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
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
            persistedRemoteConfig = stateEnabledRemoteConfig,
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
                stateSpan.hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION, 3)
            }
        )
    }

    @Test
    fun `transitions outside of sessions not recorded explicitly`() {
        val stateUpdates = listOf("foo", "bar")
        val transitions: MutableList<List<Pair<Long, String>>> = mutableListOf()
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
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
            persistedRemoteConfig = stateEnabledRemoteConfig,
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
                    stateSpan.attributes?.hasEmbraceAttributeValue(EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE, "baz")
                }
            }
        )
    }

    @Test
    fun `logs have correct current state`() {
        val stateUpdates = listOf("foo", "bar")
        val stateValues = mutableListOf<String>()
        var dataSourceKey: String? = null
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(
                    bgActivityCapture = true
                )
            ),
            persistedRemoteConfig = stateEnabledRemoteConfig,
            testCaseAction = {
                val dataSource = findDataSource<TestStateDataSource>()
                dataSourceKey = dataSource.stateAttributeKey
                stateValues.add(dataSource.getCurrentStateValue())
                embrace.logInfo("test")
                repeat(stateUpdates.size) { i ->
                    executeTransitions(listOf(stateUpdates[i]))
                    stateValues.add(dataSource.getCurrentStateValue())
                    embrace.logInfo("test")
                }
                recordSession {
                    repeat(stateUpdates.size) { i ->
                        executeTransitions(listOf(stateUpdates[i]))
                        stateValues.add(dataSource.getCurrentStateValue())
                        embrace.logInfo("test")
                    }
                }
                stateValues.add(dataSource.getCurrentStateValue())
                embrace.logInfo("test")
            },
            assertAction = {
                val logs = getSingleLogEnvelope().getLogs { it.body == "test" }
                val expectedStateValues = listOf("UNKNOWN", "foo", "bar", "foo", "bar", "bar")

                assertEquals(6, logs.size)
                repeat(logs.size) { i ->
                    assertTrue(
                        checkNotNull(logs[i].attributes).hasEmbraceAttributeValue(
                            checkNotNull(dataSourceKey),
                            expectedStateValues[i]
                        )
                    )
                    assertEquals(expectedStateValues[i], stateValues[i])
                }
            }
        )
    }

    @Test
    fun `each transition event can have its set of attributes`() {
        val attrsA = mapOf("testAttr" to "blah", "derp" to "1")
        val attrsB = mapOf("testAttr" to "orf", "derp" to "2", "id" to "123")
        val attrsC = mapOf("testAttr" to "barf", "derp" to "3")
        val transitionAttributes = listOf(attrsA, attrsB, attrsC)
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
            testCaseAction = {
                recordSession {
                    transitions = executeTransitions(
                        updates = listOf("first", "second", "C"),
                        transitionAttributes = transitionAttributes,
                    )
                }
            },
            assertAction = {
                val events = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test")?.events)
                assertEquals(3, events.size)

                // Each event carries exactly its own attrs, verified with all keys/values
                repeat(events.size) { i ->
                    events[i].assertStateTransition(
                        timestampMs = transitions[i].first,
                        newStateValue = transitions[i].second,
                        transitionAttributes = transitionAttributes[i],
                    )
                }

                assertTrue(!checkNotNull(events[0].attributes).hasEmbraceAttributeKey("id"))
                assertTrue(!checkNotNull(events[2].attributes).hasEmbraceAttributeKey("id"))

                assertTrue(checkNotNull(events[0].attributes).hasEmbraceAttributeValue("testAttr", "blah"))
                assertTrue(checkNotNull(events[1].attributes).hasEmbraceAttributeValue("testAttr", "orf"))
                assertTrue(checkNotNull(events[2].attributes).hasEmbraceAttributeValue("testAttr", "barf"))
            }
        )
    }

    @Test
    fun `transition attributes cannot override built-in state attributes`() {
        var transitions: List<Pair<Long, String>> = listOf()
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
            testCaseAction = {
                recordSession {
                    transitions = executeTransitions(
                        updates = listOf("real"),
                        transitionDrops = 3,
                        transitionAttributes = listOf(
                            mapOf(
                                EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE to "hacked",
                                EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION to "99"
                            )
                        ),
                    )
                }
            },
            assertAction = {
                val events = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test")?.events)
                events.single().assertStateTransition(
                    timestampMs = transitions[0].first,
                    newStateValue = "real",
                    droppedByInstrumentation = 3,
                )
            }
        )
    }

    @Test
    fun `transition attributes discarded when transition dropped`() {
        val attrsFirst = mapOf("foo" to "first")
        val attrsSecond = mapOf("foo" to "second")
        val attrsThird = mapOf("foo" to "third")
        val timestamps = mutableListOf<Long>()
        testRule.runTest(
            persistedRemoteConfig = stateEnabledRemoteConfig,
            testCaseAction = {
                recordSession {
                    val dataSource = findDataSource<TestStateDataSource>()
                    timestamps.add(clock.tick(100L))
                    dataSource.onStateChange("first", clock.now(), attrsFirst)
                    timestamps.add(clock.tick(100L))
                    // This is dropped so the attributes of the dropped transition are lost
                    dataSource.onStateChange("first", clock.now(), attrsSecond)
                    timestamps.add(clock.tick(100L))
                    dataSource.onStateChange("second", clock.now(), attrsThird)
                }
            },
            assertAction = {
                val events = checkNotNull(getSingleSessionEnvelope().getStateSpan("emb-state-test")?.events)
                assertEquals(2, events.size)

                events[0].assertStateTransition(
                    timestampMs = timestamps[0],
                    newStateValue = "first",
                    transitionAttributes = attrsFirst,
                )

                events[1].assertStateTransition(
                    timestampMs = timestamps[2],
                    newStateValue = "second",
                    droppedByInstrumentation = 1,
                    transitionAttributes = attrsThird,
                )
            }
        )
    }

    private fun EmbraceActionInterface.executeTransitions(
        updates: List<String>,
        transitionDrops: Int = 0,
        transitionAttributes: List<Map<String, String>> = emptyList(),
    ): List<Pair<Long, String>> {
        val transitions: MutableList<Pair<Long, String>> = mutableListOf()
        val dataSource = findDataSource<TestStateDataSource>()
        repeat(updates.size) { i ->
            transitions.add(Pair(clock.tick(100L), updates[i]))
            dataSource.onStateChange(
                newState = updates[i],
                transitionTimeMs = clock.now(),
                transitionAttributes = transitionAttributes.getOrNull(i) ?: emptyMap(),
                droppedTransitions = transitionDrops,
            )
        }
        return transitions
    }
}
