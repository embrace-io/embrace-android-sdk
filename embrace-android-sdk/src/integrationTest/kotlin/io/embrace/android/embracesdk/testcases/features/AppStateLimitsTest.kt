package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TEST_STATE_DECLARED_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.PackedStates
import io.embrace.android.embracesdk.internal.state.StateDeclaration
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateEvents
import io.embrace.android.embracesdk.internal.state.StateUpdate
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * What the app state data model does when a state changes more often than its change log lists, and what it refuses: changes past
 * a limit, updates for a state nothing declared, repeated values, attributes a state did not declare and values that are too long.
 * There is no session part in the background, so a part's limit starts again with the next foreground part.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateLimitsTest(otelSdkMode: OtelSdkMode) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.StateExportWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.StateExportWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    private val appStateOn = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
    )

    private val testStateLimit = 100
    private val overLimit = 5

    @Test
    fun `changes past a state's limit are counted and not listed but still replace the value`() {
        val lastValue = "change-${testStateLimit + overLimit}"
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    (1..testStateLimit + overLimit).forEach { i ->
                        state.onStateChange(newState = "change-$i", transitionTimeMs = clock.tick(10L))
                    }
                    embrace.logInfo("after the limit")
                    embrace.startSpan("after the limit")?.stop()
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val changeLog = envelope.changeLog()
                val events = changeLog.stateEvents("test")
                assertEquals(testStateLimit, events.size)
                assertEquals(overLimit.toString(), changeLog.attribute(AppStateKeys.DROPPED_COUNT_PREFIX + "test"))
                assertEquals(overLimit.toString(), changeLog.attribute(AppStateKeys.DROPPED_CHANGES))
                assertEquals(testStateLimit.toString(), changeLog.attribute(AppStateKeys.CHANGE_LIMIT_PREFIX + "test"))
                assertEquals(lastValue, changeLog.attribute(AppStateKeys.FINAL_VALUE_PREFIX + "test"))
                assertNull(changeLog.attribute(AppStateKeys.GLOBAL_CHANGE_LIMIT))

                val finalSeq = changeLog.long(AppStateKeys.FINAL_SEQ_PREFIX + "test")
                val lastListedSeq = events.last().long(AppStateKeys.SEQ)
                assertEquals(lastListedSeq + overLimit, finalSeq)

                // what the limit refused still took numbers and still replaced the value, so what follows is stamped right
                val log = getSingleLogEnvelope().getLogs { it.body == "after the limit" }.single()
                val stamp = checkNotNull(log.attributes)
                assertEquals(finalSeq, checkNotNull(stamp.findAttributeValue(AppStateKeys.SEQ)).toLong())
                assertEquals(lastValue to false, stamp.packedState("test"))

                val span = envelope.findSpansByName("after the limit").single()
                assertEquals(finalSeq, span.long(AppStateKeys.SEQ))
            },
        )
    }

    @Test
    fun `a state's limit starts again with the next part`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                val state = findDataSource<TestStateDataSource>()
                recordSession {
                    (1..testStateLimit + overLimit).forEach { i ->
                        state.onStateChange(newState = "first-$i", transitionTimeMs = clock.tick(10L))
                    }
                }
                recordSession {
                    state.onStateChange(newState = "next part", transitionTimeMs = clock.tick(10L))
                }
            },
            assertAction = {
                val (first, second) = getSessionEnvelopes(2)
                assertEquals(overLimit.toString(), first.changeLog().attribute(AppStateKeys.DROPPED_CHANGES))

                val changeLog = second.changeLog()
                assertEquals(listOf("next part"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_CHANGES))
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_COUNT_PREFIX + "test"))
                assertNull(changeLog.attribute(AppStateKeys.CHANGE_LIMIT_PREFIX + "test"))
                assertNull(changeLog.attribute(AppStateKeys.FINAL_VALUE_PREFIX + "test"))
                assertNull(changeLog.attribute(AppStateKeys.FINAL_SEQ_PREFIX + "test"))
            },
        )
    }

    @Test
    fun `another state still changes and is listed while one is past its limit, leaving a gap in the numbers`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    eventBus.emit(
                        StateEvents.DECLARED,
                        StateDeclaration(StateDefinition("other"), "first", false, clock.tick(10L))
                    )
                    (1..testStateLimit + overLimit).forEach { i ->
                        state.onStateChange(newState = "change-$i", transitionTimeMs = clock.tick(10L))
                    }
                    eventBus.emit(StateEvents.UPDATED, StateUpdate("other", "second", false, clock.tick(10L)))
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val listed = checkNotNull(changeLog.events).map { it.long(AppStateKeys.SEQ) }
                assertTrue(listed.zipWithNext().all { (previous, next) -> next > previous })
                assertTrue(listed.zipWithNext().any { (previous, next) -> next - previous > 1 })

                val lastTest = changeLog.stateEvents("test").last().long(AppStateKeys.SEQ)
                val lastOther = changeLog.stateEvents("other").last().long(AppStateKeys.SEQ)
                assertEquals(lastTest + overLimit + 1, lastOther)
            },
        )
    }

    @Test
    fun `an update for a state nothing declared is discarded`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    state.onStateChange(newState = "before", transitionTimeMs = clock.tick(10L))
                    eventBus.emit(StateEvents.UPDATED, StateUpdate("made-up", "value", false, clock.tick(10L)))
                    state.onStateChange(newState = "after", transitionTimeMs = clock.tick(10L))
                    embrace.logInfo("after the update")
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                assertTrue(changeLog.stateEvents("made-up").isEmpty())
                assertNull(changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "made-up"))

                // it took no number: the changes on either side of it follow each other
                val events = changeLog.stateEvents("test")
                assertEquals(listOf("before", "after"), events.map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertEquals(events[0].long(AppStateKeys.SEQ) + 1, events[1].long(AppStateKeys.SEQ))
                val initialSeq = changeLog.long(AppStateKeys.INITIAL_SEQ)
                val allSeqs = checkNotNull(changeLog.events).map { it.long(AppStateKeys.SEQ) }
                assertEquals((initialSeq + 1..initialSeq + allSeqs.size).toList(), allSeqs)

                val log = getSingleLogEnvelope().getLogs { it.body == "after the update" }.single()
                val packed = PackedStates.unpack(checkNotNull(log.attributes?.findAttributeValue(AppStateKeys.PACKED)))
                assertTrue(packed.none { it.name == "made-up" })
                assertTrue(packed.any { it.name == "test" })
            },
        )
    }

    @Test
    fun `a change keeps the attributes its state declared and the real new value`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(
                        newState = "x",
                        transitionTimeMs = clock.tick(10L),
                        transitionAttributes = mapOf(
                            TEST_STATE_DECLARED_ATTRIBUTE_KEY to "kept",
                            "undeclared" to "dropped",
                            AppStateKeys.NEW_VALUE to "forged",
                        ),
                    )
                }
            },
            assertAction = {
                val event = getSingleSessionEnvelope().changeLog().stateEvents("test").single()
                val attributes = event.attributes.orEmpty()
                assertEquals("kept", event.attribute(TEST_STATE_DECLARED_ATTRIBUTE_KEY))
                assertNull(event.attribute("undeclared"))
                assertEquals(listOf("x"), attributes.filter { it.key == AppStateKeys.NEW_VALUE }.map { it.data })
            },
        )
    }

    @Test
    fun `changes a data source reports it dropped are counted for its state alone`() {
        val reportedDropped = 3
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(
                        newState = "y",
                        transitionTimeMs = clock.tick(10L),
                        droppedTransitions = reportedDropped,
                    )
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                assertEquals(reportedDropped.toString(), changeLog.attribute(AppStateKeys.DROPPED_COUNT_PREFIX + "test"))
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_CHANGES))
                assertNull(changeLog.attribute(AppStateKeys.FINAL_VALUE_PREFIX + "test"))
                assertEquals(listOf("y"), changeLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })
            },
        )
    }

    @Test
    fun `an update that repeats the value takes no number and is not counted`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    state.onStateChange(newState = "same", transitionTimeMs = clock.tick(10L))
                    state.onStateChange(newState = "same", transitionTimeMs = clock.tick(10L))
                    state.onStateChange(newState = "same", transitionTimeMs = clock.tick(10L))
                    state.onStateChange(newState = "different", transitionTimeMs = clock.tick(10L))
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val events = changeLog.stateEvents("test")
                assertEquals(listOf("same", "different"), events.map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertEquals(events[0].long(AppStateKeys.SEQ) + 1, events[1].long(AppStateKeys.SEQ))
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_CHANGES))
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_COUNT_PREFIX + "test"))
            },
        )
    }

    @Test
    fun `a value longer than 2000 characters is cut to 2000 ending in an ellipsis`() {
        val maxLength = 2000
        val cut = "a".repeat(maxLength - 3) + "..."
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(
                        newState = "a".repeat(maxLength + 500),
                        transitionTimeMs = clock.tick(10L),
                    )
                    embrace.logInfo("after the long value")
                }
            },
            assertAction = {
                val event = getSingleSessionEnvelope().changeLog().stateEvents("test").single()
                assertEquals(cut, event.attribute(AppStateKeys.NEW_VALUE))
                assertEquals(maxLength, event.attribute(AppStateKeys.NEW_VALUE)?.length)

                val log = getSingleLogEnvelope().getLogs { it.body == "after the long value" }.single()
                assertEquals(cut to false, checkNotNull(log.attributes).packedState("test"))
            },
        )
    }

    private fun Envelope<SessionPartPayload>.changeLog(): Span = findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).single()

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.long(key: String): Long = checkNotNull(attribute(key)).toLong()

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.long(key: String): Long = checkNotNull(attribute(key)).toLong()

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    private fun List<Attribute>.packedState(state: String): Pair<String, Boolean>? =
        PackedStates.unpack(checkNotNull(findAttributeValue(AppStateKeys.PACKED)))
            .singleOrNull { it.name == state }
            ?.let { it.value to it.system }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
