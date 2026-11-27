package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.state.StatePacking
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * v2 of the runtime state data model, end to end: the change log of each session part, the stamp on each log, the link
 * on each span, what a process killed mid-part leaves for the next launch, and what a customer's exporters get. The
 * cases follow the scenarios page 6 of the design lists for the example payloads.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class StateV2FeatureTest(
    private val otelSdkMode: OtelSdkMode,
) {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    private val v2 = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
    )

    private val noBackgroundActivity = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(bgActivityCapture = false),
    )

    @Test
    fun `a session part sends one change log in place of v1's state spans, every change numbered in one order`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    executeTransitions(listOf("foo", "bar", "baz"))
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val spans = envelope.data.spans.orEmpty()
                assertNull(spans.firstOrNull { it.name == "emb-state-test" })

                val changeLog = envelope.changeLog()
                val sessionSpan = checkNotNull(envelope.getSessionPartSpan())
                assertEquals(EmbType.State.value, changeLog.attr(EmbType.State.key))
                assertEquals(sessionSpan.startTimeNanos, changeLog.startTimeNanos)
                assertEquals(sessionSpan.endTimeNanos, changeLog.endTimeNanos)
                assertEquals("UNKNOWN", changeLog.attr(StateKeys.initialValue("test")))
                assertNull(changeLog.attr(StateKeys.TRUNCATED))

                assertEquals(listOf("foo", "bar", "baz"), changeLog.eventsOf("test").map { it.attr(StateKeys.NEW_VALUE) })

                // every event of every state is in one sequence, opening just after the log's initial number
                val initialSeq = checkNotNull(changeLog.attr(StateKeys.INITIAL_SEQ)).toLong()
                val seqs = changeLog.events.orEmpty().map { checkNotNull(it.attr(StateKeys.SEQ)).toLong() }
                assertEquals((initialSeq + 1..initialSeq + seqs.size).toList(), seqs)
            },
        )
    }

    @Test
    fun `a log is stamped with every state's value and a number its change log agrees with`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    executeTransitions(listOf("foo"))
                    embrace.logInfo("after foo")
                    executeTransitions(listOf("bar"))
                    embrace.logInfo("after bar")
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val logs = getSingleLogEnvelope().getLogs { it.body?.startsWith("after") == true }
                assertEquals(2, logs.size)
                listOf("foo", "bar").forEach { value ->
                    val log = logs.single { it.body == "after $value" }
                    assertEquals(value, log.unpackedState()["test"])
                    // the log's number is the change that made the value, and the values agree with the history
                    val event = changeLog.eventsOf("test").single { it.attr(StateKeys.NEW_VALUE) == value }
                    assertEquals(event.attr(StateKeys.SEQ), log.attr(StateKeys.SEQ))
                    // v1's stamp is not sent beside v2's
                    assertNull(log.attr("emb.state.test"))
                }
            },
        )
    }

    @Test
    fun `a span carries the number before it started and the number before it ended`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    val foo = executeTransitions(listOf("foo")).single().first
                    val running = embrace.startSpan("across-change")
                    val bar = executeTransitions(listOf("bar")).single().first
                    running.stop()
                    embrace.startSpan("no-change").stop()
                    // recorded after the fact: placed by its own times in the window of recent values
                    embrace.recordCompletedSpan("after-the-fact", startTimeMs = foo + 10, endTimeMs = bar + 10)
                    embrace.recordCompletedSpan("after-the-fact-quiet", startTimeMs = foo + 10, endTimeMs = foo + 20)
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val events = envelope.changeLog().eventsOf("test")
                val fooSeq = events.single { it.attr(StateKeys.NEW_VALUE) == "foo" }.attr(StateKeys.SEQ)
                val barSeq = events.single { it.attr(StateKeys.NEW_VALUE) == "bar" }.attr(StateKeys.SEQ)
                val spans = envelope.data.spans.orEmpty()

                with(spans.single { it.name == "across-change" }) {
                    assertEquals(fooSeq, attr(StateKeys.SEQ))
                    assertEquals(barSeq, attr(StateKeys.END_SEQ))
                }
                with(spans.single { it.name == "no-change" }) {
                    assertEquals(barSeq, attr(StateKeys.SEQ))
                    assertNull(attr(StateKeys.END_SEQ))
                }
                with(spans.single { it.name == "after-the-fact" }) {
                    assertEquals(fooSeq, attr(StateKeys.SEQ))
                    assertEquals(barSeq, attr(StateKeys.END_SEQ))
                }
                with(spans.single { it.name == "after-the-fact-quiet" }) {
                    assertEquals(fooSeq, attr(StateKeys.SEQ))
                    assertNull(attr(StateKeys.END_SEQ))
                }
                // Embrace's payload carries the link and not the values
                assertTrue(spans.none { span -> span.attributes.orEmpty().any { it.key == StateKeys.value("test") } })
            },
        )
    }

    @Test
    fun `changes made with no session part are counted in the next change log, which opens where the last closed`() {
        testRule.runTest(
            instrumentedConfig = noBackgroundActivity,
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    executeTransitions(listOf("foo"))
                }
                executeTransitions(listOf("away-1", "away-2"))
                embrace.logInfo("in the background")
                recordSession()
            },
            assertAction = {
                val (first, second) = getSessionEnvelopes(2).map { it.changeLog() }
                assertEquals("2", second.attr(StateKeys.notInSessionCount("test")))
                assertEquals("away-2", second.attr(StateKeys.initialValue("test")))
                assertTrue(second.eventsOf("test").isEmpty())

                // where the first part closed, as the second part's log works it out, is where the first log ends
                val firstClosed = checkNotNull(first.attr(StateKeys.INITIAL_SEQ)).toLong() +
                    first.events.orEmpty().size + (first.attr(StateKeys.DROPPED_CHANGES)?.toLong() ?: 0)
                val notInSession = second.attributes.orEmpty()
                    .filter { it.key?.startsWith("emb.app.state.not_in_session_count.") == true }
                    .sumOf { checkNotNull(it.data).toLong() }
                assertEquals(firstClosed, checkNotNull(second.attr(StateKeys.INITIAL_SEQ)).toLong() - notInSession)

                // a log made with no session part still carries the right value
                val log = getSingleLogEnvelope().getLogs { it.body == "in the background" }.single()
                assertEquals("away-2", log.unpackedState()["test"])
            },
        )
    }

    @Test
    fun `past its limit a state's changes are counted and not listed, and its values stay right`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    executeTransitions((0 until 105).map { "v$it" })
                    embrace.logInfo("past the limit")
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val events = changeLog.eventsOf("test")
                assertEquals(100, events.size)
                assertEquals("v99", events.last().attr(StateKeys.NEW_VALUE))
                assertEquals("5", changeLog.attr(StateKeys.droppedCount("test")))
                assertEquals("5", changeLog.attr(StateKeys.DROPPED_CHANGES))
                assertEquals("100", changeLog.attr(StateKeys.changeLimit("test")))
                assertEquals("v104", changeLog.attr(StateKeys.finalValue("test")))

                // the log after the limit carries the right value, and its number is the state's last change
                val log = getSingleLogEnvelope().getLogs { it.body == "past the limit" }.single()
                assertEquals("v104", log.unpackedState()["test"])
                assertEquals(changeLog.attr(StateKeys.finalSeq("test")), log.attr(StateKeys.SEQ))
            },
        )
    }

    @Test
    fun `a process killed mid-part leaves a change log the next launch sends cut short, with spans ending at its last number`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession(endInBackground = false) {
                    executeTransitions(listOf("foo"))
                    embrace.startSpan("left-running")
                    executeTransitions(listOf("bar"))
                }
                killProcess()
            },
        )
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {},
            assertAction = {
                val envelope = getSessionEnvelopes(1, ProcessState.FOREGROUND, assertOrdering = false).single()
                val changeLog = envelope.changeLog()
                assertEquals("true", changeLog.attr(StateKeys.TRUNCATED))
                assertEquals(listOf("foo", "bar"), changeLog.eventsOf("test").map { it.attr(StateKeys.NEW_VALUE) })
                // a truncated log has no counts of what a limit refused
                assertNull(changeLog.attr(StateKeys.DROPPED_CHANGES))

                val lastSeq = changeLog.events.orEmpty().maxOf { checkNotNull(it.attr(StateKeys.SEQ)).toLong() }
                val fooSeq = changeLog.eventsOf("test").first().attr(StateKeys.SEQ)
                with(envelope.data.spans.orEmpty().single { it.name == "left-running" }) {
                    assertEquals(fooSeq, attr(StateKeys.SEQ))
                    assertEquals(lastSeq.toString(), attr(StateKeys.END_SEQ))
                }
            },
        )
    }

    @Test
    fun `a hosted SDK reports into a state a data source defines, and cannot make a state by reporting a name`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    EmbraceInternalApi.internalInterface.reportState("test", "from-hosted", clock.now() - 5)
                    EmbraceInternalApi.internalInterface.reportState("made-up", "nope", clock.now())
                    embrace.logInfo("after hosted")
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val event = changeLog.eventsOf("test").single()
                assertEquals("from-hosted", event.attr(StateKeys.NEW_VALUE))
                assertTrue(changeLog.eventsOf("made-up").isEmpty())
                assertNull(changeLog.attr(StateKeys.initialValue("made-up")))

                val stamp = getSingleLogEnvelope().getLogs { it.body == "after hosted" }.single().unpackedState()
                assertEquals("from-hosted", stamp["test"])
                assertFalse(stamp.containsKey("made-up"))
            },
        )
    }

    @Test
    fun `changes reported from two threads at once are given one order`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    val dataSource = findDataSource<TestStateDataSource>()
                    val start = CountDownLatch(1)
                    val threads = listOf("a", "b").map { prefix ->
                        thread {
                            start.await()
                            repeat(40) { dataSource.onStateChange("$prefix$it", clock.now()) }
                        }
                    }
                    start.countDown()
                    threads.forEach(Thread::join)
                }
            },
            assertAction = {
                val events = getSingleSessionEnvelope().changeLog().eventsOf("test")
                val seqs = events.map { checkNotNull(it.attr(StateKeys.SEQ)).toLong() }
                assertEquals(seqs.sorted(), seqs)
                assertEquals(seqs.size, seqs.toSet().size)
                assertEquals(80, events.size)
            },
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `a customer's exporters get each state's value on a log and the changes as events`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    executeTransitions(listOf("foo"))
                    embrace.logInfo("exported")
                }
            },
            assertAction = {
                // the replayed changes never reach Embrace's own payload
                val logs = getSingleLogEnvelope().getLogs { true }
                assertTrue(logs.none { it.attr(StateKeys.NAME) != null })
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.body.asString() == "exported" }.single().attributes.toStringMap()
                assertEquals("foo", log[StateKeys.value("test")])
                assertNull(log[StateKeys.PACKED])
                assertNotNull(log[StateKeys.SEQ])

                val change = awaitLogs(1) {
                    val attrs = it.attributes.toStringMap()
                    attrs[StateKeys.NAME] == "test" && attrs[StateKeys.NEW_VALUE] == "foo"
                }.single().attributes.toStringMap()
                assertEquals("UNKNOWN", change[StateKeys.PREVIOUS_VALUE])
                assertEquals(StateKeys.ENTITY_TYPE_PROCESS, change[StateKeys.ENTITY_TYPE])
                assertNull(change["emb.export_only"])
                assertNull(change["emb.session_part_id"])
            },
        )
    }

    @Test
    fun `with only the change log switched on, logs keep a stamp and spans carry no link`() {
        testRule.runTest(
            persistedRemoteConfig = v2.copy(pctStateAssociationEnabled = 0f),
            testCaseAction = {
                recordSession {
                    executeTransitions(listOf("foo"))
                    embrace.startSpan("unlinked").stop()
                    embrace.logInfo("change log only")
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                assertEquals(listOf("foo"), envelope.changeLog().eventsOf("test").map { it.attr(StateKeys.NEW_VALUE) })
                assertNull(envelope.data.spans.orEmpty().single { it.name == "unlinked" }.attr(StateKeys.SEQ))
                val log = getSingleLogEnvelope().getLogs { it.body == "change log only" }.single()
                assertEquals("foo", log.unpackedState()["test"])
                assertNull(log.attr("emb.state.test"))
            },
        )
    }

    @Test
    fun `a value is cut at 2,000 characters and an attribute its data source did not declare is dropped`() {
        val long = "x".repeat(2500)
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    findDataSource<TestStateDataSource>().onStateChange(
                        newState = long,
                        transitionTimeMs = clock.tick(100L),
                        transitionAttributes = mapOf("undeclared" to "dropped"),
                    )
                }
            },
            assertAction = {
                val event = getSingleSessionEnvelope().changeLog().eventsOf("test").single()
                assertEquals("x".repeat(1997) + "...", event.attr(StateKeys.NEW_VALUE))
                assertNull(event.attr("undeclared"))
            },
        )
    }

    @Test
    fun `a span given a start older than the window of recent values carries no link`() {
        testRule.runTest(
            persistedRemoteConfig = v2,
            testCaseAction = {
                recordSession {
                    val before = clock.tick(100L)
                    executeTransitions((0 until 70).map { "v$it" })
                    embrace.recordCompletedSpan("too-old", startTimeMs = before, endTimeMs = clock.now())
                }
            },
            assertAction = {
                with(getSingleSessionEnvelope().data.spans.orEmpty().single { it.name == "too-old" }) {
                    assertNull(attr(StateKeys.SEQ))
                    assertNull(attr(StateKeys.END_SEQ))
                }
            },
        )
    }

    @Test
    fun `v1 is untouched while v2 is off`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctStateCaptureEnabledV2 = 100f, pctMultiFilePersistenceEnabled = 100f),
            testCaseAction = {
                recordSession {
                    executeTransitions(listOf("foo"))
                    embrace.logInfo("v1")
                }
            },
            assertAction = {
                val spans = getSingleSessionEnvelope().data.spans.orEmpty()
                assertNull(spans.firstOrNull { it.name == StateKeys.CHANGE_LOG_SPAN_NAME })
                assertNotNull(spans.firstOrNull { it.name == "emb-state-test" })
                val log = getSingleLogEnvelope().getLogs { it.body == "v1" }.single()
                assertEquals("foo", log.attr("emb.state.test"))
                assertNull(log.attr(StateKeys.PACKED))
            },
        )
    }

    private fun EmbraceActionInterface.executeTransitions(updates: List<String>): List<Pair<Long, String>> {
        val dataSource = findDataSource<TestStateDataSource>()
        return updates.map { value ->
            val time = clock.tick(100L)
            dataSource.onStateChange(newState = value, transitionTimeMs = time)
            Pair(time, value)
        }
    }

    /**
     * Leaves the session part open on disk, as a process killed without notice would.
     */
    private fun killProcess() {
        testRule.setup.getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).shutdown()
        testRule.services.networkConnectivityService.close()
        testRule.services.otelSdkConfig.close()
        testRule.services.workerThreadPool.close()
    }

    private fun Envelope<SessionPartPayload>.changeLog(): Span =
        data.spans.orEmpty().single { it.name == StateKeys.CHANGE_LOG_SPAN_NAME }

    private fun Span.attr(key: String): String? = attributes.orEmpty().findAttributeValue(key)

    private fun Span.eventsOf(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == StateKeys.changeEventName(state) }

    private fun SpanEvent.attr(key: String): String? = attributes.orEmpty().findAttributeValue(key)

    private fun Log.attr(key: String): String? = attributes.orEmpty().findAttributeValue(key)

    private fun Log.unpackedState(): Map<String, String> = buildMap {
        StatePacking.unpack(checkNotNull(attr(StateKeys.PACKED))) { name, value, _ -> put(name, value) }
    }

    internal companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
}
