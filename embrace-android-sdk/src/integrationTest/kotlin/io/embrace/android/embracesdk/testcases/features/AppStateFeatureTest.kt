package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpanByName
import io.embrace.android.embracesdk.assertions.findSpansByName
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.assertions.getLogs
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.fakes.TestStateValue
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
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
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.opentelemetry.api.common.AttributeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * The app state data model as a session part's payload, its logs and an app's own exporters see it: one change log span for a
 * part, a stamp on every log and two numbers on every span.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class AppStateFeatureTest(otelSdkMode: OtelSdkMode) {

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

    /**
     * A value the SDK generated, as the test state reports one. Its text is whatever the value prints as.
     */
    private val generatedValue = TestStateValue.SystemValue("sys")

    private val appStateOn = RemoteConfig(
        pctStateCaptureEnabledV2 = 100f,
        pctSharedStateSpanEnabled = 100f,
        pctStateAssociationEnabled = 100f,
        pctMultiFilePersistenceEnabled = 100f,
        // a session part is always in progress, so each part's change log opens where the one before closed
        backgroundActivityConfig = BackgroundActivityRemoteConfig(100f),
    )

    @Test
    fun `a session part has one change log that lists its changes in the order of one sequence`() {
        val changeTimes = mutableListOf<Long>()
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    listOf("foo", generatedValue, "bar").forEach { value ->
                        changeTimes.add(clock.tick(100L))
                        state.onStateChange(newState = value, transitionTimeMs = clock.now())
                    }
                    // an update that repeats the value is not a change
                    state.onStateChange(newState = "bar", transitionTimeMs = clock.tick(100L))
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val sessionSpan = envelope.findSessionPartSpan()
                val changeLog = envelope.changeLog()

                // one span for every state, in place of a span for each
                assertEquals(1, envelope.findSpansOfType(EmbType.State).size)
                assertTrue(envelope.findSpansByName("emb-state-test").isEmpty())
                assertTrue(sessionSpan.links.orEmpty().none { it.spanId == changeLog.spanId })

                assertEquals(sessionSpan.startTimeNanos, changeLog.startTimeNanos)
                assertEquals(sessionSpan.endTimeNanos, changeLog.endTimeNanos)
                assertEquals(Span.Status.UNSET, changeLog.status)
                assertNull(changeLog.attribute(AppStateKeys.TRUNCATED))
                assertNull(changeLog.attribute(AppStateKeys.DROPPED_CHANGES))

                // it opens with every state's value, as of the last change before the part
                val initialSeq = checkNotNull(changeLog.attribute(AppStateKeys.INITIAL_SEQ)).toLong()
                assertTrue(initialSeq > 0)
                assertEquals("UNKNOWN", changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "test"))
                assertNull(changeLog.attribute(AppStateKeys.INITIAL_VALUE_TYPE_PREFIX + "test"))

                // then lists each change, with its number, in order
                val events = changeLog.stateEvents("test")
                assertEquals(listOf("foo", "$generatedValue", "bar"), events.map { it.attribute(AppStateKeys.NEW_VALUE) })
                assertEquals(listOf(null, "system", null), events.map { it.attribute(AppStateKeys.NEW_VALUE_TYPE) })
                assertEquals(changeTimes.map { it.millisToNanos() }, events.map { it.timestampNanos })
                val allSeqs = checkNotNull(changeLog.events).map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() }
                assertEquals((initialSeq + 1..initialSeq + allSeqs.size).toList(), allSeqs)

                // the part's own span is linked to the log by the numbers at its start and its end
                assertEquals(initialSeq.toString(), sessionSpan.attribute(AppStateKeys.SEQ))
                assertEquals(allSeqs.last().toString(), sessionSpan.attribute(AppStateKeys.END_SEQ))
            },
        )
    }

    @Test
    fun `a log carries every state's value at that moment, packed for Embrace and plain for an exporter`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    embrace.logInfo("before")
                    state.onStateChange(newState = "foo;bar=baz", transitionTimeMs = clock.tick(100L))
                    embrace.logInfo("after")
                    state.onStateChange(newState = generatedValue, transitionTimeMs = clock.tick(100L))
                    embrace.logInfo("generated")
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val changeSeqs = changeLog.stateEvents("test").map { checkNotNull(it.attribute(AppStateKeys.SEQ)).toLong() }
                val logs = getSingleLogEnvelope().getLogs { it.body in setOf("before", "after", "generated") }.associateBy { it.body }

                val before = checkNotNull(logs.getValue("before").attributes)
                val after = checkNotNull(logs.getValue("after").attributes)
                val generated = checkNotNull(logs.getValue("generated").attributes)

                // the number on a log is that of the last change before it, which the change log lists
                assertEquals((changeSeqs[0] - 1).toString(), before.findAttributeValue(AppStateKeys.SEQ))
                assertEquals(changeSeqs[0].toString(), after.findAttributeValue(AppStateKeys.SEQ))
                assertEquals(changeSeqs[1].toString(), generated.findAttributeValue(AppStateKeys.SEQ))

                assertEquals("UNKNOWN" to false, before.packedState("test"))
                assertEquals("foo;bar=baz" to false, after.packedState("test"))
                assertEquals("$generatedValue" to true, generated.packedState("test"))
                // a separator inside a value is escaped, so that it cannot corrupt the states after it
                assertTrue(checkNotNull(after.findAttributeValue(AppStateKeys.PACKED)).contains("test=foo\\;bar\\=baz"))

                // Embrace's payload has the packed form only, and none of the earlier model's attribute for each state
                listOf(before, after, generated).forEach { attributes ->
                    assertTrue(attributes.none { checkNotNull(it.key).startsWith(AppStateKeys.VALUE_PREFIX) })
                    assertTrue(attributes.none { checkNotNull(it.key).startsWith("emb.state.") })
                }
            },
            otelExportAssertion = {
                val exported = awaitLogs(3) { it.bodyValue?.asString() in setOf("before", "after", "generated") }
                    .associateBy { it.bodyValue?.asString() }
                val before = exported.getValue("before").attributes
                val after = exported.getValue("after").attributes
                val generated = exported.getValue("generated").attributes

                assertEquals("UNKNOWN", before.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"])
                assertEquals("foo;bar=baz", after.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"])
                assertEquals("$generatedValue", generated.toStringMap()[AppStateKeys.VALUE_PREFIX + "test"])
                assertNull(after.toStringMap()[AppStateKeys.VALUE_TYPE_PREFIX + "test"])
                assertEquals("system", generated.toStringMap()[AppStateKeys.VALUE_TYPE_PREFIX + "test"])

                // the number is a whole number, and the packed form is Embrace's alone
                val seqKey = AttributeKey.longKey(AppStateKeys.SEQ)
                assertEquals(checkNotNull(before.get(seqKey)) + 1, after.get(seqKey))
                assertEquals(checkNotNull(after.get(seqKey)) + 1, generated.get(seqKey))
                assertFalse(after.toStringMap().containsKey(AppStateKeys.PACKED))
            },
        )
    }

    @Test
    fun `a span carries the number of the last change before it started and before it ended`() {
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    val state = findDataSource<TestStateDataSource>()
                    val unchanged = checkNotNull(embrace.startSpan("unchanged"))
                    clock.tick(100L)
                    unchanged.stop()

                    val changedUnder = checkNotNull(embrace.startSpan("changed-under"))
                    val beforeChangeMs = clock.now()
                    state.onStateChange(newState = "foo", transitionTimeMs = clock.tick(100L))
                    val firstChangeMs = clock.now()
                    state.onStateChange(newState = "bar", transitionTimeMs = clock.tick(100L))
                    val secondChangeMs = clock.now()
                    clock.tick(100L)
                    changedUnder.stop()

                    // spans given their times after the fact are placed among the recent changes by those times
                    embrace.recordCompletedSpan("recorded-across-first-change", beforeChangeMs, firstChangeMs + 50)
                    embrace.recordCompletedSpan("recorded-between-changes", firstChangeMs, secondChangeMs - 1)
                    embrace.recordCompletedSpan("recorded-ending-at-second-change", firstChangeMs + 1, secondChangeMs)
                }
            },
            assertAction = {
                val envelope = getSingleSessionEnvelope()
                val seqs = envelope.changeLog().stateEvents("test").map { checkNotNull(it.attribute(AppStateKeys.SEQ)) }
                val beforeChanges = (seqs[0].toLong() - 1).toString()

                val unchanged = envelope.findSpanByName("unchanged")
                assertEquals(beforeChanges, unchanged.attribute(AppStateKeys.SEQ))
                assertNull(unchanged.attribute(AppStateKeys.END_SEQ))

                val changedUnder = envelope.findSpanByName("changed-under")
                assertEquals(beforeChanges, changedUnder.attribute(AppStateKeys.SEQ))
                assertEquals(seqs[1], changedUnder.attribute(AppStateKeys.END_SEQ))

                val acrossFirst = envelope.findSpanByName("recorded-across-first-change")
                assertEquals(beforeChanges, acrossFirst.attribute(AppStateKeys.SEQ))
                assertEquals(seqs[0], acrossFirst.attribute(AppStateKeys.END_SEQ))

                // a time that ties with a change's counts as after the change
                val between = envelope.findSpanByName("recorded-between-changes")
                assertEquals(seqs[0], between.attribute(AppStateKeys.SEQ))
                assertNull(between.attribute(AppStateKeys.END_SEQ))

                val endingAtSecond = envelope.findSpanByName("recorded-ending-at-second-change")
                assertEquals(seqs[0], endingAtSecond.attribute(AppStateKeys.SEQ))
                assertEquals(seqs[1], endingAtSecond.attribute(AppStateKeys.END_SEQ))

                // a span carries the link and never the values
                checkNotNull(envelope.data.spans).filter { it.name != AppStateKeys.CHANGE_LOG_SPAN_NAME }.forEach { span ->
                    assertTrue(span.attributes.orEmpty().none { AppStateKeys.isCustomerValueKey(checkNotNull(it.key)) })
                }
            },
            otelExportAssertion = {
                val changedUnder = awaitSpans(1) { it.name == "changed-under" }.single().attributes
                val seq = checkNotNull(changedUnder.get(AttributeKey.longKey(AppStateKeys.SEQ)))
                assertEquals(seq + 2, changedUnder.get(AttributeKey.longKey(AppStateKeys.END_SEQ)))
                assertNull(changedUnder.get(AttributeKey.stringKey(AppStateKeys.SEQ)))

                // the default where the app receives logs is association only: the numbers and no values
                assertTrue(changedUnder.toStringMap().keys.none(AppStateKeys::isCustomerValueKey))

                // the change log is Embrace's own shape, and no exporter gets it or a span for each state
                assertTrue(awaitSpans(0) { it.name == AppStateKeys.CHANGE_LOG_SPAN_NAME || it.name == "emb-state-test" }.isEmpty())
            },
        )
    }

    @Test
    fun `a hosted SDK reports into a state a data source defines, and cannot make a state by naming one`() {
        var reportedAtMs = 0L
        testRule.runTest(
            persistedRemoteConfig = appStateOn,
            testCaseAction = {
                recordSession {
                    reportedAtMs = clock.now()
                    clock.tick(250L)
                    // the hosted side's own time for the change is passed across, and is earlier than when it is recorded
                    EmbraceInternalApi.internalInterface.reportState("test", "route/home", reportedAtMs)
                    EmbraceInternalApi.internalInterface.reportState("hosted-only", "value", reportedAtMs)
                    embrace.logInfo("on the hosted screen")
                }
            },
            assertAction = {
                val changeLog = getSingleSessionEnvelope().changeLog()
                val change = changeLog.stateEvents("test").single()
                assertEquals("route/home", change.attribute(AppStateKeys.NEW_VALUE))
                assertEquals(reportedAtMs.millisToNanos(), change.timestampNanos)

                // nothing is recorded, stamped or sent for a state no data source defines
                assertTrue(changeLog.stateEvents("hosted-only").isEmpty())
                assertNull(changeLog.attribute(AppStateKeys.INITIAL_VALUE_PREFIX + "hosted-only"))
                // the report is refused and tracked as an applied limit, once for the one report
                assertEquals(
                    "1",
                    getSingleSessionEnvelope().findSessionPartSpan().attribute("emb.private.applied_limit.undeclared_state_update.drop"),
                )
                val log = checkNotNull(getSingleLogEnvelope().getLogs { it.body == "on the hosted screen" }.single().attributes)
                assertEquals("route/home" to false, log.packedState("test"))
                assertNull(log.packedState("hosted-only"))
                assertEquals(change.attribute(AppStateKeys.SEQ), log.findAttributeValue(AppStateKeys.SEQ))
            },
        )
    }

    private fun Envelope<SessionPartPayload>.changeLog(): Span = findSpanByName(AppStateKeys.CHANGE_LOG_SPAN_NAME)

    private fun Span.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun SpanEvent.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateEvents(state: String): List<SpanEvent> =
        events.orEmpty().filter { it.name == AppStateKeys.CHANGE_LOG_EVENT_PREFIX + state }

    /**
     * The value the packed stamp among these log attributes has for [state], and whether it is marked as generated.
     */
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
