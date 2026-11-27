package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
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
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * What the app state data model does at the edges of session parts: changes made while no part is open, the change log that
 * continues from one part to the next, and a state that is first declared while a part is open.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStatePartBoundaryTest(otelSdkMode: OtelSdkMode) {

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

    private val withBackgroundActivity = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    @Test
    fun `changes made in the background with no part open are counted by the next part and listed by none`() {
        val changeCount = 3
        val changes = (1..changeCount).map { "bg-$it" }
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession { }
                val state = findDataSource<TestStateDataSource>()
                changes.forEach { value ->
                    state.onStateChange(newState = value, transitionTimeMs = clock.tick(100L))
                }
                embrace.logInfo("in the background")
                clock.tick(100L)
                recordSession { }
            },
            assertAction = {
                val (first, second) = getSessionEnvelopes(2)
                val firstLog = first.changeLog()
                val secondLog = second.changeLog()

                assertTrue(firstLog.stateEvents("test").none { it.attribute(AppStateKeys.NEW_VALUE) in changes })
                assertTrue(secondLog.stateEvents("test").none { it.attribute(AppStateKeys.NEW_VALUE) in changes })
                assertEquals(changeCount.toString(), secondLog.attribute(AppStateKeys.NOT_IN_SESSION_COUNT_PREFIX + "test"))
                assertEquals(changes.last(), secondLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))

                // a log emitted in the background is stamped with the value and number of the last change
                val log = getSingleLogEnvelope().getLogs { it.body == "in the background" }.single()
                val stamp = checkNotNull(log.attributes)
                val lastSeq = checkNotNull(stamp.findAttributeValue(AppStateKeys.SEQ)).toLong()
                assertEquals(changes.last() to false, stamp.packedState("test"))
                assertEquals(lastSeq, secondLog.long(AppStateKeys.INITIAL_SEQ))
                assertContinuity(firstLog, secondLog)
            },
        )
    }

    @Test
    fun `many changes made while no part is open each take a number and nothing is dropped`() {
        val changeCount = 250
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession { }
                val state = findDataSource<TestStateDataSource>()
                repeat(changeCount) { i ->
                    state.onStateChange(newState = "many-$i", transitionTimeMs = clock.tick(1L))
                }
                embrace.logInfo("after many")
                clock.tick(100L)
                recordSession {
                    // the state's limit starts again with each part
                    state.onStateChange(newState = "in the next part", transitionTimeMs = clock.tick(100L))
                }
            },
            assertAction = {
                val (first, second) = getSessionEnvelopes(2)
                val firstLog = first.changeLog()
                val secondLog = second.changeLog()

                assertEquals(changeCount.toString(), secondLog.attribute(AppStateKeys.NOT_IN_SESSION_COUNT_PREFIX + "test"))
                assertEquals("many-${changeCount - 1}", secondLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))
                assertEquals(
                    firstLog.long(AppStateKeys.INITIAL_SEQ) + firstLog.events.orEmpty().size + changeCount,
                    secondLog.long(AppStateKeys.INITIAL_SEQ)
                )
                assertNull(firstLog.attribute(AppStateKeys.DROPPED_CHANGES))
                assertNull(secondLog.attribute(AppStateKeys.DROPPED_CHANGES))
                assertNull(secondLog.attribute(AppStateKeys.DROPPED_COUNT_PREFIX + "test"))
                assertEquals(listOf("in the next part"), secondLog.stateEvents("test").map { it.attribute(AppStateKeys.NEW_VALUE) })

                val log = getSingleLogEnvelope().getLogs { it.body == "after many" }.single()
                assertEquals(
                    secondLog.long(AppStateKeys.INITIAL_SEQ),
                    checkNotNull(log.attributes?.findAttributeValue(AppStateKeys.SEQ)).toLong()
                )
            },
        )
    }

    @Test
    fun `each part has one change log and each part starts where the one before it ended`() {
        testRule.runTest(
            persistedRemoteConfig = withBackgroundActivity,
            testCaseAction = {
                val state = findDataSource<TestStateDataSource>()
                recordSession {
                    state.onStateChange(newState = "foreground one", transitionTimeMs = clock.tick(100L))
                }
                state.onStateChange(newState = "background", transitionTimeMs = clock.tick(100L))
                recordSession {
                    state.onStateChange(newState = "foreground two", transitionTimeMs = clock.tick(100L))
                }
            },
            assertAction = {
                val parts = allParts()
                assertEquals(4, parts.size)
                parts.forEach { part ->
                    assertEquals(1, part.findSpansOfType(EmbType.State).size)
                    assertEquals(1, part.findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).size)
                }
                parts.zipWithNext().forEach { (previous, next) ->
                    assertContinuity(previous.changeLog(), next.changeLog())
                }
                // nothing falls between background activity parts, so nothing is counted as made outside of one
                assertTrue(
                    parts.drop(1).all { part ->
                        part.changeLog().attributes.orEmpty().none { checkNotNull(it.key).startsWith(AppStateKeys.NOT_IN_SESSION_COUNT_PREFIX) }
                    }
                )
            },
        )
    }

    @Test
    fun `a part with no changes still has a change log with its opening values and no events`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession { }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                assertNotNull(changeLog.attribute(AppStateKeys.INITIAL_SEQ))
                assertEquals("UNKNOWN", changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))
                assertTrue(changeLog.stateEvents("test").isEmpty())
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_CHANGES))
            },
        )
    }

    @Test
    fun `a span that runs across two parts is a snapshot in the first and carries both numbers in the second`() {
        lateinit var spanName: String
        testRule.runTest(
            persistedRemoteConfig = withBackgroundActivity,
            testCaseAction = {
                val state = findDataSource<TestStateDataSource>()
                spanName = "across-parts"
                val span = checkNotNull(embrace.startSpan(spanName))
                recordSession {
                    state.onStateChange(newState = "while running", transitionTimeMs = clock.tick(100L))
                }
                state.onStateChange(newState = "after the first part", transitionTimeMs = clock.tick(100L))
                recordSession {
                    clock.tick(100L)
                    assertTrue(span.stop())
                }
            },
            assertAction = {
                val (first, second) = getSessionEnvelopes(2)
                val snapshot = checkNotNull(first.data.spanSnapshots).single { it.name == spanName }
                assertNotNull(snapshot.attribute(AppStateKeys.SEQ))
                assertNull(snapshot.attribute(AppStateKeys.END_SEQ))
                assertTrue(first.findSpansByName(spanName).isEmpty())

                val ended = second.findSpansByName(spanName).single()
                val startSeq = checkNotNull(ended.attribute(AppStateKeys.SEQ)).toLong()
                val endSeq = checkNotNull(ended.attribute(AppStateKeys.END_SEQ)).toLong()
                assertEquals(snapshot.attribute(AppStateKeys.SEQ), startSeq.toString())
                assertTrue(endSeq > startSeq)
            },
        )
    }

    @Test
    fun `a state first declared inside a part has no opening value there and has one in the next part`() {
        testRule.runTest(
            persistedRemoteConfig = withBackgroundActivity,
            testCaseAction = {
                recordSession {
                    eventBus.emit(
                        StateEvents.DECLARED,
                        StateDeclaration(StateDefinition("late"), "first", false, clock.tick(100L))
                    )
                }
                recordSession { }
            },
            assertAction = {
                val (first, second) = getSessionEnvelopes(2)
                val firstLog = first.changeLog()
                assertNull(firstLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "late"))
                assertEquals(listOf("first"), firstLog.stateEvents("late").map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertEquals("first", second.changeLog().attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "late"))
                assertTrue(second.changeLog().stateEvents("late").isEmpty())
            },
        )
    }

    private fun Envelope<SessionPartPayload>.changeLog(): Span = findSpansByName(AppStateKeys.CHANGE_LOG_SPAN_NAME).single()

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.long(key: String): Long = checkNotNull(attribute(key)).toLong()

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    private fun List<Attribute>.packedState(state: String): Pair<String, Boolean>? =
        PackedStates.unpack(checkNotNull(findAttributeValue(AppStateKeys.PACKED)))
            .singleOrNull { it.name == state }
            ?.let { it.value to it.system }

    private fun EmbracePayloadAssertionInterface.allParts():
        List<Envelope<SessionPartPayload>> =
        (getSessionEnvelopes(2) + getSessionEnvelopes(2, state = ProcessState.BACKGROUND).distinctBy { it.findSessionPartSpan().spanId })
            .sortedBy { it.findSessionPartSpan().startTimeNanos }

    /**
     * The next part's opening number, less what was made outside any part, is where this part ended.
     */
    private fun assertContinuity(previous: Span, next: Span) {
        val notInSession = next.attributes.orEmpty()
            .filter { checkNotNull(it.key).startsWith(AppStateKeys.NOT_IN_SESSION_COUNT_PREFIX) }
            .sumOf { checkNotNull(it.data).toLong() }
        val dropped = previous.attribute(AppStateKeys.DROPPED_CHANGES)?.toLong() ?: 0L
        assertEquals(
            previous.long(AppStateKeys.INITIAL_SEQ) + previous.events.orEmpty().size + dropped,
            next.long(AppStateKeys.INITIAL_SEQ) - notInSession
        )
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
