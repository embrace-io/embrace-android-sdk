package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.hasLinkToEmbraceSpan
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testcases.persistence.MultiFilePersistenceParityTestBase
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * State changes recorded in a journal and delivered with their session part. A journal needs a session part's telemetry
 * to be persisted as multiple files, and is only meant for the Kotlin OpenTelemetry implementation, so nothing here
 * applies to the single-file mode or to the other implementation.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class StateJournalFeatureTest(
    persistenceMode: PersistenceMode,
    private val otelSdkMode: OtelSdkMode,
) : MultiFilePersistenceParityTestBase(persistenceMode, otelSdkMode) {

    @Before
    fun multiFileAndKotlinOpenTelemetryOnly() {
        assumeTrue(persistenceMode == PersistenceMode.MULTI_FILE)
        assumeTrue(otelSdkMode == OtelSdkMode.REGULAR)
    }

    @Test
    fun `changes of every state are delivered in the order they were made, as one span of their session part`() {
        testRule.runTest(
            persistedRemoteConfig = journaledConfig(),
            testCaseAction = {
                recordSession {
                    changeTestState("foo")
                    embrace.addUserPersona("beta")
                    changeTestState("bar")
                    embrace.logInfo("after-bar")
                    changeTestState("baz")
                }
            },
            assertAction = {
                val part = deliveredParts().single()
                val stateSpan = part.stateSpan()
                val sessionPartSpan = part.findSessionPartSpan()
                assertEquals(sessionPartSpan.startTimeNanos, stateSpan.startTimeNanos)
                assertEquals(sessionPartSpan.endTimeNanos, stateSpan.endTimeNanos)
                assertTrue(sessionPartSpan.hasLinkToEmbraceSpan(stateSpan, LinkType.State))

                // the test state and the personas were both captured before this part started
                val attributes = checkNotNull(stateSpan.attributes)
                assertEquals("UNKNOWN", attributes.findAttributeValue("emb.app.state.initial_value.test"))
                assertNotNull(attributes.findAttributeValue("emb.app.state.initial_value.personas"))

                // a state the SDK defines and a piece of user metadata, in one order
                val events = stateSpan.eventsAfterPartStart()
                val changes = events.filter { it.name == "emb-state-test" || it.name == "emb-state-personas" }
                assertEquals(
                    listOf("emb-state-test", "emb-state-personas", "emb-state-test", "emb-state-test"),
                    changes.map { it.name },
                )
                assertEquals("foo", changes[0].newValue())
                // the personas are one value: every persona the user has, in order
                assertTrue("beta" in checkNotNull(changes[1].newValue()).split(","))
                assertEquals("bar", changes[2].newValue())
                assertEquals("baz", changes[3].newValue())

                assertNull(attributes.findAttributeValue("emb.app.state.truncated"))

                // the log was written between two changes, and says so: it carries the sequence of the change before it,
                // and the value every state had then, which is what the span leads to at that sequence
                val log = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                val loggedSequence = checkNotNull(log.findAttributeValue("emb.app.state.seq")).toLong()
                assertEquals(stateSpan.sequenceOf(changes[2]), loggedSequence)
                assertEquals(stateSpan.valuesAt(loggedSequence), decodeStates(checkNotNull(log.findAttributeValue("emb.app.state.packed"))))
            },
        )
    }

    @Test
    fun `changes made with no session part in progress are delivered with the next part to start`() {
        testRule.runTest(
            persistedRemoteConfig = journaledConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                changeTestState("in-background")
                recordSession {
                    changeTestState("in-foreground")
                }
            },
            assertAction = {
                val stateSpan = deliveredParts().single().stateSpan()
                val partStartSequence = checkNotNull(stateSpan.attributes?.findAttributeValue("emb.app.state.part_start_seq")).toLong()
                val testChanges = checkNotNull(stateSpan.events).filter { it.name == "emb-state-test" }
                assertEquals(listOf("UNKNOWN", "in-background", "in-foreground"), testChanges.map { it.newValue() })
                assertEquals(
                    listOf(true, true, false),
                    testChanges.map { stateSpan.sequenceOf(it) <= partStartSequence },
                )
            },
        )
    }

    @Test
    fun `a state that reaches its limit is counted and no longer listed, while the others are and every log still has its value`() {
        testRule.runTest(
            persistedRemoteConfig = journaledConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession {
                    // three more changes than a state with no limit of its own lists in a session part
                    repeat(TEST_STATE_LIMIT + 3) { changeTestState("value-$it") }
                    embrace.addUserPersona("beta")
                    embrace.logInfo("after-limit")
                }
                recordSession {
                    changeTestState("next-part")
                }
            },
            assertAction = {
                val parts = deliveredParts(2).sortedBy { it.findSessionPartSpan().startTimeNanos }
                val (limited, next) = parts.map { it.stateSpan() }
                val attributes = checkNotNull(limited.attributes)

                // the first hundred changes of the part are listed, and the personas go on being listed after them
                val events = limited.eventsAfterPartStart()
                assertEquals(
                    (0 until TEST_STATE_LIMIT).map { "value-$it" },
                    events.filter { it.name == "emb-state-test" }.map { it.newValue() },
                )
                val personas = events.last { it.name == "emb-state-personas" }
                assertTrue(limited.sequenceOf(personas) > limited.sequenceOf(events.last { it.name == "emb-state-test" }) + 3)

                // what is missing is counted for the part and for the state, with the limit that refused it and the value
                // the state ended the part with. No other state is counted, and the limit every state shares was not reached.
                assertEquals("3", attributes.findAttributeValue("emb.app.state.dropped_changes"))
                assertEquals("3", attributes.findAttributeValue("emb.app.state.dropped_count.test"))
                assertEquals("$TEST_STATE_LIMIT", attributes.findAttributeValue("emb.app.state.change_limit.test"))
                assertEquals("value-${TEST_STATE_LIMIT + 2}", attributes.findAttributeValue("emb.app.state.final_value.test"))
                assertEquals(
                    listOf("emb.app.state.change_limit.test", "emb.app.state.dropped_count.test", "emb.app.state.final_value.test"),
                    attributes.mapNotNull { it.key }.filter { key -> PER_STATE_COUNT_PREFIXES.any(key::startsWith) }.sorted(),
                )
                assertNull(attributes.findAttributeValue("emb.app.state.global_change_limit"))
                assertNull(attributes.findAttributeValue("emb.app.state.truncated"))
                // each refused change is tracked under the name the state's data source has, as its own limit would be
                val sessionPartAttributes = checkNotNull(parts.first().findSessionPartSpan().attributes)
                assertEquals("3", sessionPartAttributes.findAttributeValue("emb.private.applied_limit.test_state_data_source.drop"))
                assertNull(sessionPartAttributes.findAttributeValue("emb.private.applied_limit.state_change.drop"))

                // the log written after the limit was reached has the state's value as it was then, which the events of
                // its part no longer lead to, at a number the span does not list
                val log = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                val logged = decodeStates(checkNotNull(log.findAttributeValue("emb.app.state.packed")))
                assertEquals("value-${TEST_STATE_LIMIT + 2}", logged["test"])
                assertEquals("value-${TEST_STATE_LIMIT - 1}", limited.valuesAt(Long.MAX_VALUE)["test"])
                assertEquals(limited.sequenceOf(personas), checkNotNull(log.findAttributeValue("emb.app.state.seq")).toLong())

                // the next part opens with the value the last ended with, and counts from nothing again
                val nextAttributes = checkNotNull(next.attributes)
                assertEquals("value-${TEST_STATE_LIMIT + 2}", nextAttributes.findAttributeValue("emb.app.state.initial_value.test"))
                assertEquals(listOf("next-part"), next.eventsAfterPartStart().filter { it.name == "emb-state-test" }.map { it.newValue() })
                assertNull(nextAttributes.findAttributeValue("emb.app.state.dropped_changes"))
                assertNull(nextAttributes.findAttributeValue("emb.app.state.dropped_count.test"))
            },
        )
    }

    @Test
    fun `the power state is present from the start, as unknown and then as the mode that was read`() {
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(powerSaveCapture = true)),
            persistedRemoteConfig = journaledConfig(backgroundActivity = BackgroundActivityRemoteConfig(0f)),
            testCaseAction = {
                recordSession {
                    embrace.logInfo("in-session")
                }
            },
            assertAction = {
                // power saving was never on, and the state is there all the same: with a span for each state it would not be
                val stateSpan = deliveredParts().single().stateSpan()
                assertEquals(
                    listOf("unknown", "normal"),
                    checkNotNull(stateSpan.events).filter { it.name == "emb-state-power" }.map { it.newValue() },
                )
                val log = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                assertEquals("normal", decodeStates(checkNotNull(log.findAttributeValue("emb.app.state.packed")))["power"])
            },
        )
    }

    @Test
    fun `changes made in a session part that crashed are delivered with it by the next launch`() {
        testRule.runTest(
            persistedRemoteConfig = journaledConfig(),
            testCaseAction = {
                recordSession {
                    changeTestState("before-crash")
                    simulateJvmUncaughtException(RuntimeException("Boom!"))
                }
            },
            assertAction = {},
        )

        // tear the crashed process down so the next launch reads the shared storage from scratch
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()

        testRule.runTest(
            persistedRemoteConfig = journaledConfig(),
            testCaseAction = {},
            assertAction = {
                val stateSpan = deliveredParts().single().stateSpan()
                assertEquals(
                    listOf("before-crash"),
                    stateSpan.eventsAfterPartStart().filter { it.name == "emb-state-test" }.map { it.newValue() },
                )
                // the crash ended the part, and its journal with it
                assertNotNull(stateSpan.endTimeNanos)
                assertEquals(Span.Status.UNSET, stateSpan.status)
                assertNull(stateSpan.attributes?.findAttributeValue("emb.app.state.truncated"))
            },
        )
    }

    @Test
    fun `changes made in a session part the process was killed in are delivered by the next launch, with the limit a state reached`() {
        testRule.runTest(
            persistedRemoteConfig = journaledConfig(),
            testCaseAction = {
                recordSession(endInBackground = false) {
                    // two more changes than the part lists of the state, and then a change of another state
                    repeat(TEST_STATE_LIMIT + 2) { changeTestState("before-kill-$it") }
                    embrace.addUserPersona("beta")
                }
                // nothing ends the part or its journal: writes that were not made yet are lost with the process
                persistenceWorkers.forEach { worker ->
                    testRule.setup.getFakedWorkerExecutor(worker).apply {
                        blockingMode = true
                        shutdownNow()
                    }
                }
                testRule.services.intakeService.shutdown()
            },
            assertAction = {
                assertEquals(0, getSessionEnvelopes(0).size)
            },
        )

        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()

        testRule.runTest(
            persistedRemoteConfig = journaledConfig(),
            testCaseAction = {},
            assertAction = {
                val part = deliveredParts().single()
                val stateSpan = part.stateSpan()
                val events = stateSpan.eventsAfterPartStart()
                assertEquals(
                    (0 until TEST_STATE_LIMIT).map { "before-kill-$it" },
                    events.filter { it.name == "emb-state-test" }.map { it.newValue() },
                )
                // the journal has no end, so the span was completed the way the part's other unfinished spans were, and
                // says that the values after its last event are not known from it
                assertEquals(part.findSessionPartSpan().endTimeNanos, stateSpan.endTimeNanos)
                assertEquals(Span.Status.ERROR, stateSpan.status)
                val attributes = checkNotNull(stateSpan.attributes)
                assertEquals("true", attributes.findAttributeValue("emb.app.state.truncated"))

                // the limit was recorded as the state reached it, so the span says whose changes the gap in its numbers
                // can be, and the state that changed after the gap has every change listed. The count and the final
                // value are the part end's, which was never written.
                assertEquals("$TEST_STATE_LIMIT", attributes.findAttributeValue("emb.app.state.change_limit.test"))
                assertEquals(
                    listOf("emb.app.state.change_limit.test"),
                    attributes.mapNotNull { it.key }.filter { key -> PER_STATE_COUNT_PREFIXES.any(key::startsWith) },
                )
                assertNull(attributes.findAttributeValue("emb.app.state.dropped_changes"))
                assertNull(attributes.findAttributeValue("emb.app.state.global_change_limit"))
                val personas = events.last { it.name == "emb-state-personas" }
                assertTrue(stateSpan.sequenceOf(personas) > stateSpan.sequenceOf(events.last { it.name == "emb-state-test" }) + 2)
            },
        )
    }

    private fun journaledConfig(backgroundActivity: BackgroundActivityRemoteConfig? = null): RemoteConfig {
        val config = if (backgroundActivity != null) remoteConfig(backgroundActivity) else remoteConfig()
        return config.copy(pctStateCaptureEnabledV2 = 100.0f, pctSharedStateSpanEnabled = 100.0f)
    }

    private fun EmbraceActionInterface.changeTestState(value: String) {
        findDataSource<TestStateDataSource>().onStateChange(newState = value, transitionTimeMs = clock.tick(100L))
    }

    private fun Envelope<SessionPartPayload>.stateSpan(): Span = findSpansOfType(EmbType.State).single().also {
        assertEquals("emb-states", it.name)
    }

    private fun Span.eventsAfterPartStart(): List<SpanEvent> {
        val partStartSequence = checkNotNull(attributes?.findAttributeValue("emb.app.state.part_start_seq")).toLong()
        return checkNotNull(events).filter { sequenceOf(it) > partStartSequence }
    }

    /**
     * The place of [event] in the order of state changes, which the event carries as `emb.app.state.seq`. The events of a span
     * are in that order, and the numbers skip where a limit refused a change, so an event's number is at least its position
     * after the sequence the span's initial values are as of.
     */
    private fun Span.sequenceOf(event: SpanEvent): Long {
        val initialSequence = checkNotNull(attributes?.findAttributeValue("emb.app.state.initial_seq")).toLong()
        val sequence = checkNotNull(event.attributes?.findAttributeValue("emb.app.state.seq")).toLong()
        assertTrue(sequence >= initialSequence + checkNotNull(events).indexOfFirst { it === event } + 1)
        return sequence
    }

    /**
     * The value of every state as of the change numbered [sequence], worked out from the span: the values it opened with,
     * then each change up to that one.
     */
    private fun Span.valuesAt(sequence: Long): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        checkNotNull(attributes).forEach { attribute ->
            val key = checkNotNull(attribute.key)
            if (key.startsWith(INITIAL_VALUE_PREFIX)) {
                values[key.removePrefix(INITIAL_VALUE_PREFIX)] = checkNotNull(attribute.data)
            }
        }
        checkNotNull(events).filter { sequenceOf(it) <= sequence }.forEach { event ->
            values[checkNotNull(event.name).removePrefix(STATE_EVENT_PREFIX)] = checkNotNull(event.newValue())
        }
        return values
    }

    /**
     * The value of every state a log was stamped with. None of the values here need the encoding's escapes.
     */
    private fun decodeStates(encoded: String): Map<String, String> = encoded.split(";").associate { pair ->
        val separator = pair.indexOfFirst { it == '=' || it == '~' }
        pair.substring(0, separator) to pair.substring(separator + 1)
    }

    private fun SpanEvent.newValue(): String? = attributes?.findAttributeValue("emb.app.state.new_value")

    private companion object {
        const val INITIAL_VALUE_PREFIX = "emb.app.state.initial_value."
        const val STATE_EVENT_PREFIX = "emb-state-"

        /**
         * How many changes of the test state a session part lists, as its data source gives no limit of its own.
         */
        const val TEST_STATE_LIMIT = 100

        /**
         * The starts of the attributes a change log has for a state it does not list every change of.
         */
        val PER_STATE_COUNT_PREFIXES = listOf("emb.app.state.dropped_count.", "emb.app.state.change_limit.", "emb.app.state.final_value")

        val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )
    }
}
