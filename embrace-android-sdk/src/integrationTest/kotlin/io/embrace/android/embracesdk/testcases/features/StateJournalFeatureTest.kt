package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.hasLinkToEmbraceSpan
import io.embrace.android.embracesdk.fakes.TestStateDataSource
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
                assertEquals("UNKNOWN", attributes.findAttributeValue("emb.states.initial_value.test"))
                assertNotNull(attributes.findAttributeValue("emb.states.initial_value.personas"))

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

                assertNull(attributes.findAttributeValue("emb.states.truncated"))

                // the log was written between two changes, and says so: it carries the sequence of the change before it,
                // and the value every state had then, which is what the span leads to at that sequence
                val log = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                val loggedSequence = checkNotNull(log.findAttributeValue("emb.states.seq")).toLong()
                assertEquals(stateSpan.sequenceOf(changes[2]), loggedSequence)
                assertEquals(stateSpan.valuesAt(loggedSequence), decodeStates(checkNotNull(log.findAttributeValue("emb.states.packed"))))
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
                val partStartSequence = checkNotNull(stateSpan.attributes?.findAttributeValue("emb.states.part_start_seq")).toLong()
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
                assertNull(stateSpan.attributes?.findAttributeValue("emb.states.truncated"))
            },
        )
    }

    @Test
    fun `changes made in a session part the process was killed in are delivered with it by the next launch`() {
        testRule.runTest(
            persistedRemoteConfig = journaledConfig(),
            testCaseAction = {
                recordSession(endInBackground = false) {
                    changeTestState("before-kill")
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
                assertEquals(
                    listOf("before-kill"),
                    stateSpan.eventsAfterPartStart().filter { it.name == "emb-state-test" }.map { it.newValue() },
                )
                // the journal has no end, so the span was completed the way the part's other unfinished spans were, and
                // says that the values after its last event are not known from it
                assertEquals(part.findSessionPartSpan().endTimeNanos, stateSpan.endTimeNanos)
                assertEquals(Span.Status.ERROR, stateSpan.status)
                assertEquals("true", stateSpan.attributes?.findAttributeValue("emb.states.truncated"))
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
        val partStartSequence = checkNotNull(attributes?.findAttributeValue("emb.states.part_start_seq")).toLong()
        return checkNotNull(events).filter { sequenceOf(it) > partStartSequence }
    }

    /**
     * The place of [event] in the order of state changes, which the event carries as `emb.states.seq`. The changes a span
     * holds are consecutive, so it also equals the event's position after the sequence the span's initial values are as of.
     */
    private fun Span.sequenceOf(event: SpanEvent): Long {
        val initialSequence = checkNotNull(attributes?.findAttributeValue("emb.states.initial_seq")).toLong()
        val counted = initialSequence + checkNotNull(events).indexOfFirst { it === event } + 1
        assertEquals(counted, checkNotNull(event.attributes?.findAttributeValue("emb.states.seq")).toLong())
        return counted
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

    private fun SpanEvent.newValue(): String? = attributes?.findAttributeValue("emb.states.new_value")

    private companion object {
        const val INITIAL_VALUE_PREFIX = "emb.states.initial_value."
        const val STATE_EVENT_PREFIX = "emb-state-"

        val persistenceWorkers = listOf(
            Worker.Background.SessionPersistenceWorker,
            Worker.Background.PeriodicCacheWorker,
        )
    }
}
