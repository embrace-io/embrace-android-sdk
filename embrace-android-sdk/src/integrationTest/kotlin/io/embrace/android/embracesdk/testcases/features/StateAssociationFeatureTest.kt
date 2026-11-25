package io.embrace.android.embracesdk.testcases.features

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.findSpansOfType
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.TestStateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_PERIODIC_CACHE_INTERVAL_MS
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testcases.persistence.MultiFilePersistenceParityTestBase
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.tracing.data.SpanData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Telemetry associated with state: the link on a span and the stamp on a log, in Embrace's payload and in what an app's
 * own exporters and processors are handed, and what else those are handed in each mode. It builds on the journal of state
 * changes, so it applies where that does: multi-file persistence and the Kotlin OpenTelemetry implementation.
 *
 * The worker that emits state events is faked, and emits nothing until a test tells it to, so that an event is built
 * later than its change was made, as it can be on a device.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
internal class StateAssociationFeatureTest(
    persistenceMode: PersistenceMode,
    private val otelSdkMode: OtelSdkMode,
) : MultiFilePersistenceParityTestBase(persistenceMode, otelSdkMode, listOf(Worker.Background.StateExportWorker)) {

    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var endedSpans: MutableList<SpanData>
    private lateinit var processedLogs: MutableList<LogRecordData>
    private var eventsBeforeFirstPart: List<LogRecordData> = emptyList()

    @Before
    fun multiFileAndKotlinOpenTelemetryOnly() {
        assumeTrue(persistenceMode == PersistenceMode.MULTI_FILE)
        assumeTrue(otelSdkMode == OtelSdkMode.REGULAR)
        spanExporter = FakeSpanExporter()
        logExporter = FakeLogRecordExporter()
        endedSpans = mutableListOf()
        processedLogs = mutableListOf()
    }

    @Test
    fun `verbose - an app's exporters get the values on every span and every change event, and Embrace's payload gets none`() {
        runScenario("verbose") {
            listOf(exportedSpan(ACROSS), endedSpan(ACROSS)).forEach { across ->
                assertEquals("in-background", across.attributes["emb.states.value.test"])
                assertEquals("foo", across.attributes["emb.states.end_value.test"])
                // a state that did not change while the span ran has a value at its start, and none at its end
                assertTrue(across.attributes.containsKey("emb.states.value.personas"))
                assertFalse(across.attributes.containsKey("emb.states.end_value.personas"))
            }
            with(exportedSpan(STEADY)) {
                assertEquals("foo", attributes["emb.states.value.test"])
                assertEquals(emptyList<String>(), attributes.keys.filter { it.startsWith("emb.states.end_value") })
            }
            // the values of a span that is given its times are the ones that held at those times
            listOf(exportedSpan(AFTER_THE_FACT), endedSpan(AFTER_THE_FACT)).forEach { afterTheFact ->
                assertEquals("in-background", afterTheFact.attributes["emb.states.value.test"])
                assertEquals("foo", afterTheFact.attributes["emb.states.end_value.test"])
            }
            listOf(exportedSpan(ENDED_EARLIER), endedSpan(ENDED_EARLIER)).forEach { endedEarlier ->
                assertEquals("in-background", endedEarlier.attributes["emb.states.value.test"])
                assertEquals(emptyList<String>(), endedEarlier.attributes.keys.filter { it.startsWith("emb.states.end_value") })
            }

            // each change event carries the value of every state after that change
            val changes = stateEvents().filter { it.eventName == CHANGE_EVENT }
            changes.forEach { change ->
                assertEquals(change.attributes["emb.states.new_value"], change.attributes["emb.states.value.${change.stateName()}"])
            }
            // And those are the values after it, not the ones when the event was built: the events of the session part
            // were all built once it was over, when the state had its last value.
            assertEquals("foo", changes.single { it.newValue() == "foo" }.attributes["emb.states.value.test"])
            assertEquals(LAST_VALUE, changes.last { it.stateName() == "test" }.newValue())
        }
    }

    @Test
    fun `association - an app's exporters get the link alone on a span and each change alone`() {
        runScenario("association") {
            (spanExporter.exportedSpans + endedSpans).forEach { span ->
                assertEquals(emptyList<String>(), span.attributes.keys.filter(::isStateValue))
            }
            stateEvents().filter { it.eventName == CHANGE_EVENT }.forEach { change ->
                assertEquals(emptyList<String>(), change.attributes.keys.filter(::isStateValue))
            }
        }
    }

    @Test
    fun `off - an app's exporters get the link on a span and the stamp on a log, and no events`() {
        runScenario("off", expectEvents = false) {
            (spanExporter.exportedSpans + endedSpans).forEach { span ->
                assertEquals(emptyList<String>(), span.attributes.keys.filter(::isStateValue))
            }
            assertEquals(emptyList<LogRecordData>(), stateEvents())
            assertEquals(emptyList<LogRecordData>(), processedLogs.filter { it.eventName != null })
            assertFalse(exportedSpan(ACROSS).resource.attributes.containsKey("emb.process_identifier"))
        }
    }

    @Test
    fun `with the switch off, no span carries anything of state, and a span is not handed to the SDK as it ends`() {
        testRule.runTest(
            persistedRemoteConfig = associatedConfig("verbose").copy(pctStateAssociationEnabled = 0.0f),
            preSdkStartAction = {
                embrace.addSpanExporter(spanExporter)
                embrace.addLogRecordExporter(logExporter)
                embrace.addSpanProcessor(FakeSpanProcessor(onEndAction = { endedSpans.add(it.toSpanData()) }))
            },
            testCaseAction = {
                changeTestState("in-background")
                recordSession {
                    val across = embrace.startSpan(ACROSS)
                    val changeTimeMs = changeTestState("foo")
                    across.stop()
                    embrace.recordCompletedSpan(AFTER_THE_FACT, changeTimeMs - 20, changeTimeMs)
                    embrace.logInfo(LOG_MESSAGE)
                    embrace.startSpan(LEFT_OPEN)
                }
            },
            assertAction = {
                val part = deliveredParts().single()
                // with the switch off a log keeps the packed stamp, for Embrace's payload and an app's exporter alike
                val deliveredLog = getLogEnvelopes(1).flatMap { checkNotNull(it.data.logs) }.single { it.body == LOG_MESSAGE }
                val exported = logExporter.exportedLogs.single { it.body == LOG_MESSAGE }
                assertEquals(deliveredLog.attribute("emb.states.packed"), exported.attributes["emb.states.packed"])
                assertEquals("foo", states(deliveredLog)["test"]?.first)
                assertEquals(deliveredLog.attribute("emb.states.seq"), exported.attributes["emb.states.seq"])
                assertEquals(emptyList<String>(), exported.attributes.keys.filter { it.startsWith("emb.states.value") })
                // the change log is still delivered, as the span it was before
                val stateSpan = part.findSpansOfType(EmbType.State).single()
                val delivered = (checkNotNull(part.data.spans) + checkNotNull(part.data.spanSnapshots)) - stateSpan
                assertTrue(delivered.map { it.name }.containsAll(listOf(ACROSS, AFTER_THE_FACT, LEFT_OPEN)))
                delivered.forEach { span -> assertEquals(span.name, emptyList<String>(), span.stateKeys()) }
                assertTrue(spanExporter.exportedSpans.map { it.name }.containsAll(listOf(ACROSS, AFTER_THE_FACT)))
                (spanExporter.exportedSpans + endedSpans).forEach { span ->
                    assertEquals(span.name, emptyList<String>(), span.attributes.keys.filter { it.startsWith("emb.states.") })
                }
                assertFalse(testRule.services.otelSdkConfig.spanProcessor.isOnEndingRequired())
            },
        )
    }

    @Test
    fun `a span that is given a time is linked to it only while the snapshot that held then is among the last 64`() {
        var forgottenMs = 0L
        testRule.runTest(
            persistedRemoteConfig = associatedConfig("off"),
            testCaseAction = {
                recordSession {
                    val endForgotten = embrace.startSpan(END_FORGOTTEN)
                    forgottenMs = changeTestState("forgotten")
                    repeat(REMEMBERED_CHANGES + 5) { changeTestState("value-$it") }
                    clock.tick(100)

                    // its start is from before the oldest snapshot that is remembered, and so is the end of the next one
                    embrace.recordCompletedSpan(START_FORGOTTEN, forgottenMs + 10, clock.now() - 10)
                    endForgotten.stop(endTimeMs = forgottenMs + 10)
                    embrace.recordCompletedSpan(AFTER_THE_FACT, clock.now() - 20, clock.now() - 10)
                    // and this one started just before the last change, and ended after it
                    embrace.recordCompletedSpan(ACROSS, clock.now() - 150, clock.now() - 10)
                }
            },
            assertAction = {
                val part = deliveredParts().single()
                val stateSpan = part.findSpansOfType(EmbType.State).single()
                val spans = checkNotNull(part.data.spans)

                // nothing can be known of the start, so there is no link at all
                assertEquals(emptyList<String>(), spans.single { it.name == START_FORGOTTEN }.stateKeys())

                // nothing can be known of the end, so the span keeps the number it started with and has none for its end
                val endForgotten = spans.single { it.name == END_FORGOTTEN }
                assertEquals(listOf("emb.states.seq"), endForgotten.stateKeys())
                assertEquals("UNKNOWN", stateSpan.valuesAt(checkNotNull(endForgotten.stateAttribute("emb.states.seq")).toLong())["test"])

                val lastValue = "value-${REMEMBERED_CHANGES + 4}"
                val afterTheFact = spans.single { it.name == AFTER_THE_FACT }
                assertEquals(listOf("emb.states.seq"), afterTheFact.stateKeys())
                assertEquals(lastValue, stateSpan.valuesAt(checkNotNull(afterTheFact.stateAttribute("emb.states.seq")).toLong())["test"])

                val across = spans.single { it.name == ACROSS }
                val startSequence = checkNotNull(across.stateAttribute("emb.states.seq")).toLong()
                assertEquals("value-${REMEMBERED_CHANGES + 3}", stateSpan.valuesAt(startSequence)["test"])
                assertEquals((startSequence + 1).toString(), across.stateAttribute("emb.states.end_seq"))
            },
        )
    }

    @Test
    fun `a span in progress when its process dies is delivered by the next launch with the number it started with`() {
        testRule.runTest(
            persistedRemoteConfig = associatedConfig("off"),
            testCaseAction = {
                recordSession(endInBackground = false) {
                    persistDueWrites()
                    changeTestState("before-the-span")
                    embrace.startSpan(BEFORE_LEFT_OPEN).stop()
                    embrace.startSpan(LEFT_OPEN)
                    // a change made while it is in progress, which a snapshot of it says nothing of
                    changeTestState("after-the-span")
                    embrace.startSpan(AFTER_LEFT_OPEN).stop()
                    clock.tick(DEFAULT_PERIODIC_CACHE_INTERVAL_MS)
                    persistDueWrites()
                }
                // the process dies: every write still waiting dies with it
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
            persistedRemoteConfig = associatedConfig("off"),
            testCaseAction = {},
            assertAction = {
                val part = deliveredParts().single()
                val spans = checkNotNull(part.data.spans) + part.data.spanSnapshots.orEmpty()
                val leftOpen = spans.single { it.name == LEFT_OPEN }
                val before = checkNotNull(spans.single { it.name == BEFORE_LEFT_OPEN }.stateAttribute("emb.states.seq"))
                val after = checkNotNull(spans.single { it.name == AFTER_LEFT_OPEN }.stateAttribute("emb.states.seq"))

                // what the next launch read back is the snapshot of the span that was persisted, which has its link, and the
                // number of the last change the dead part's change log lists as its end, as a change came while it ran
                assertEquals(listOf("emb.states.seq", "emb.states.end_seq"), leftOpen.stateKeys())
                assertEquals(before, leftOpen.stateAttribute("emb.states.seq"))
                assertEquals(after, leftOpen.stateAttribute("emb.states.end_seq"))
                assertEquals(before.toLong() + 1, after.toLong())
            },
        )
    }

    /**
     * Runs one scenario with what an app's exporters and processors are given set to [exportMode], asserts everything that
     * does not depend on that, and then [assertMode].
     *
     * The scenario: a state changes with no session part in progress, as background activity is off. Then, in a session
     * part, a span runs across a change of that state, and another does that is told afterwards that it ended before the
     * change. A span starts and ends after the change, a span is recorded after the fact with a start from before the
     * change and an end from after it, a log is written, and a span starts that is still in progress when the part ends.
     * The state changes once more at the end, and only then are the state events of the part emitted.
     */
    private fun runScenario(exportMode: String, expectEvents: Boolean = true, assertMode: () -> Unit) {
        var changeTimeMs = 0L
        testRule.runTest(
            persistedRemoteConfig = associatedConfig(exportMode),
            preSdkStartAction = {
                embrace.addSpanExporter(spanExporter)
                embrace.addLogRecordExporter(logExporter)
                embrace.addSpanProcessor(FakeSpanProcessor(onEndAction = { endedSpans.add(it.toSpanData()) }))
                embrace.addLogRecordProcessor(FakeLogRecordProcessor { processedLogs.add(it.toLogRecordData()) })
            },
            testCaseAction = {
                changeTestState("in-background")
                // an app's processor is handed the change once it is emitted, and does not wait for a session part to start
                emitStateEvents()
                eventsBeforeFirstPart = processedLogs.filter { it.eventName != null }
                recordSession {
                    val across = embrace.startSpan(ACROSS)
                    val endedEarlier = embrace.startSpan(ENDED_EARLIER)
                    changeTimeMs = changeTestState("foo")
                    clock.tick(50)
                    across.stop()
                    endedEarlier.stop(endTimeMs = changeTimeMs - 1)
                    embrace.startSpan(STEADY).stop()
                    embrace.recordCompletedSpan(AFTER_THE_FACT, changeTimeMs - 20, changeTimeMs + 10)
                    embrace.logInfo(LOG_MESSAGE)
                    embrace.startSpan(LEFT_OPEN)
                    changeTestState(LAST_VALUE)
                }
                emitStateEvents()
            },
            assertAction = {
                val part = deliveredParts().single()
                val stateSpan = part.findSpansOfType(EmbType.State).single()
                assertEmbracePayload(part, stateSpan)
                assertExportedSpansAndLog(part, stateSpan)
                if (expectEvents) {
                    assertExportedEvents(part, stateSpan, changeTimeMs)
                }
                assertMode()
            },
        )
    }

    /**
     * Embrace's own payload has the link on spans, the packed attribute `emb.states.packed` and the sequence number on a log, and the
     * change log as one span. It has none of what is made for an app's exporters.
     */
    private fun EmbracePayloadAssertionInterface.assertEmbracePayload(part: Envelope<SessionPartPayload>, stateSpan: Span) {
        assertEquals("emb-states", stateSpan.name)
        val spans = checkNotNull(part.data.spans)

        // the two numbers resolve to values through the change log in the same payload
        val across = spans.single { it.name == ACROSS }
        val startSequence = checkNotNull(across.stateAttribute("emb.states.seq")).toLong()
        val endSequence = checkNotNull(across.stateAttribute("emb.states.end_seq")).toLong()
        assertEquals("in-background", stateSpan.valuesAt(startSequence)["test"])
        assertEquals("foo", stateSpan.valuesAt(endSequence)["test"])
        assertEquals(startSequence + 1, endSequence)

        val steady = spans.single { it.name == STEADY }
        assertEquals(endSequence, steady.stateAttribute("emb.states.seq")?.toLong())
        assertNull(steady.stateAttribute("emb.states.end_seq"))

        // a span recorded after the fact has the numbers that held at the times it was given, which are from before and
        // after the change, and not the one that held when it was recorded
        with(spans.single { it.name == AFTER_THE_FACT }) {
            assertEquals(startSequence, stateAttribute("emb.states.seq")?.toLong())
            assertEquals(endSequence, stateAttribute("emb.states.end_seq")?.toLong())
        }
        // a span that is told after the change that it ended before it has no number for its end
        with(spans.single { it.name == ENDED_EARLIER }) {
            assertEquals(startSequence, stateAttribute("emb.states.seq")?.toLong())
            assertNull(stateAttribute("emb.states.end_seq"))
        }
        // a span still in progress has the number it started with, in the snapshot of it the part is delivered with
        val leftOpen = checkNotNull(part.data.spanSnapshots).single { it.name == LEFT_OPEN }
        assertEquals(listOf("emb.states.seq"), leftOpen.stateKeys())
        assertEquals(endSequence, leftOpen.stateAttribute("emb.states.seq")?.toLong())

        // and no span has a value
        assertEquals(
            emptyList<String>(),
            (spans + checkNotNull(part.data.spanSnapshots)).flatMap { it.stateKeys() }.filter(::isStateValue),
        )

        val logs = getLogEnvelopes(1).flatMap { checkNotNull(it.data.logs) }
        val log = logs.single { it.body == LOG_MESSAGE }
        assertEquals(endSequence.toString(), log.attribute("emb.states.seq"))
        // Embrace's payload has one packed attribute for the states, and none of the plain ones an app's exporters get
        assertEquals(stateSpan.valuesAt(endSequence), decodeStates(checkNotNull(log.attribute("emb.states.packed"))))
        assertEquals(
            emptyList<String>(),
            checkNotNull(log.attributes).mapNotNull { it.key }.filter { it.startsWith("emb.states.value") },
        )
        // the same log, as an app's exporter and processor were handed it, has the same values in the plain form
        listOf(logExporter.exportedLogs, processedLogs).forEach { exportedLogs ->
            val exported = exportedLogs.single { it.body == LOG_MESSAGE }
            val plain = exported.attributes.filterKeys { it.startsWith(VALUE_PREFIX) }
                .map { it.key.removePrefix(VALUE_PREFIX) to it.value }.toMap()
            assertEquals(decodeStates(checkNotNull(log.attribute("emb.states.packed"))), plain)
            assertEquals(log.attribute("emb.states.seq"), exported.attributes["emb.states.seq"].toString())
            assertEquals(
                states(log).filterValues { it.second }.keys,
                exported.attributes.keys.filter { it.startsWith(VALUE_TYPE_PREFIX) }.map { it.removePrefix(VALUE_TYPE_PREFIX) }.toSet(),
            )
        }
        // no event that describes state is a log of Embrace's
        assertEquals(emptyList<Log>(), logs.filter { it.attribute("emb.states.name") != null || it.attribute("emb.entity.id") != null })
    }

    /**
     * An app's exporter and processor are handed the same link on a span and the same stamp on a log as Embrace's payload
     * has.
     */
    private fun assertExportedSpansAndLog(part: Envelope<SessionPartPayload>, stateSpan: Span) {
        val spans = checkNotNull(part.data.spans)
        listOf(ACROSS, STEADY, AFTER_THE_FACT, ENDED_EARLIER).forEach { name ->
            val delivered = spans.single { it.name == name }
            listOf(exportedSpan(name), endedSpan(name)).forEach { exported ->
                listOf("emb.states.seq", "emb.states.end_seq").forEach { key ->
                    assertEquals(delivered.stateAttribute(key)?.toLong(), exported.attributes[key])
                }
            }
        }

        val sequence = checkNotNull(exportedSpan(STEADY).attributes["emb.states.seq"]) as Long
        listOf(logExporter.exportedLogs, processedLogs).forEach { logs ->
            val log = logs.single { it.body == LOG_MESSAGE }
            // a whole number, and never the packed attribute that Embrace's payload has
            assertEquals(sequence, log.attributes["emb.states.seq"])
            assertFalse(log.attributes.containsKey("emb.states.packed"))
            assertEquals(
                stateSpan.valuesAt(sequence),
                log.attributes.filterKeys { it.startsWith(VALUE_PREFIX) }.map { it.key.removePrefix(VALUE_PREFIX) to it.value }.toMap(),
            )
        }
    }

    /**
     * An app's log exporter and log record processor are handed every change in order, the one made with no session part
     * in progress when it was made, and the value of every state when the session part started.
     */
    private fun assertExportedEvents(part: Envelope<SessionPartPayload>, stateSpan: Span, changeTimeMs: Long) {
        val events = stateEvents()
        assertEquals(events.map { it.eventName to it.attributes }, processedLogs.filter { it.eventName != null }.map { it.eventName to it.attributes })
        val processIdentifier = checkNotNull(part.findSessionPartSpan().attributes?.findAttributeValue("emb.process_identifier"))

        events.forEach { event ->
            assertNull(event.body)
            assertEquals(SeverityNumber.INFO, event.severityNumber)
            assertEquals("state", event.attributes["emb.type"])
            assertEquals("process", event.attributes["emb.entity.type"])
            assertEquals(processIdentifier, event.attributes["emb.entity.id"])
            assertEquals(processIdentifier, event.resource.attributes["emb.process_identifier"])
            // it belongs to the process and not to a session part, and nothing a log is stamped with is on it
            assertEquals(emptyList<String>(), event.attributes.keys.filter { it.contains("session") || it == "log.record.uid" })
        }

        // every change of the process so far, each once and in order, and the snapshot where the session part started
        val changes = events.filter { it.eventName == CHANGE_EVENT }
        val snapshot = events.single { it.eventName == SNAPSHOT_EVENT }
        assertEquals((1L..changes.size).toList(), changes.map { it.attributes["emb.states.seq"] })
        val inBackground = changes.single { it.newValue() == "in-background" }
        val foo = changes.single { it.newValue() == "foo" }
        assertEquals("test", foo.stateName())
        // a change says what the state had before, in both renderings, and the state's first change and the snapshot say none
        assertEquals("in-background", foo.attributes["emb.states.previous_value"])
        assertEquals("foo", changes.single { it.newValue() == LAST_VALUE }.attributes["emb.states.previous_value"])
        assertNull(changes.first { it.stateName() == "test" }.attributes["emb.states.previous_value"])
        assertEquals(emptyList<String>(), snapshot.attributes.keys.filter { it.contains("previous") })
        assertEquals(changeTimeMs.millisToNanos(), foo.timestamp)
        // the change made with no part in progress, then the part starting, then the change made in it. Starting the part
        // changes other states too, so the two changes are not next to the snapshot.
        assertEquals(events.indexOf(inBackground) + 1, events.indexOf(snapshot))
        assertTrue(events.indexOf(snapshot) < events.indexOf(foo))
        assertTrue(eventsBeforeFirstPart.any { it.newValue() == "in-background" })
        assertTrue(eventsBeforeFirstPart.none { it.eventName == SNAPSHOT_EVENT })

        // the snapshot is every value as the part started, which is what the change log says they were then
        val partStartSequence = checkNotNull(stateSpan.attributes?.findAttributeValue("emb.states.part_start_seq")).toLong()
        assertEquals(partStartSequence, snapshot.attributes["emb.states.seq"])
        assertEquals(inBackground.attributes["emb.states.seq"], snapshot.attributes["emb.states.seq"])
        assertTrue(checkNotNull(snapshot.timestamp) >= checkNotNull(inBackground.timestamp))
        assertEquals(
            stateSpan.valuesAt(partStartSequence),
            snapshot.attributes.filterKeys { it.startsWith(VALUE_PREFIX) }.map { it.key.removePrefix(VALUE_PREFIX) to it.value }.toMap(),
        )

        // the span that ran across the change started at the number before it, and ended at its number
        assertEquals("UNKNOWN", changes.first { it.stateName() == "test" }.newValue())
        assertEquals(foo.attributes["emb.states.seq"], exportedSpan(ACROSS).attributes["emb.states.end_seq"])
    }

    /**
     * The remote config that associates telemetry with state, and gives an app's exporters and processors [exportMode].
     */
    private fun associatedConfig(exportMode: String): RemoteConfig = remoteConfig(BackgroundActivityRemoteConfig(0f)).copy(
        pctStateCaptureEnabledV2 = 100.0f,
        pctSharedStateSpanEnabled = 100.0f,
        pctStateAssociationEnabled = 100.0f,
        stateExportMode = exportMode,
    )

    /**
     * Emits the state events that have been queued since this was last called, on the worker that emits them.
     */
    private fun emitStateEvents() {
        testRule.setup.getFakedWorkerExecutor(Worker.Background.StateExportWorker).runCurrentlyBlocked()
    }

    /**
     * Runs every persistence write that is due by now.
     */
    private fun persistDueWrites() {
        persistenceWorkers.forEach { worker ->
            testRule.setup.getFakedWorkerExecutor(worker).runCurrentlyBlocked()
        }
    }

    private fun EmbraceActionInterface.changeTestState(value: String): Long {
        val time = clock.tick(100L)
        findDataSource<TestStateDataSource>().onStateChange(newState = value, transitionTimeMs = time)
        return time
    }

    /**
     * Every state a log's packed attribute holds, by name to its value and whether the SDK generated it. None of the
     * values here need the encoding's escapes.
     */
    private fun states(log: Log): Map<String, Pair<String, Boolean>> =
        checkNotNull(log.attribute("emb.states.packed")).split(";").associate { pair ->
            val separator = pair.indexOfFirst { it == '=' || it == '~' }
            pair.substring(0, separator) to (pair.substring(separator + 1) to (pair[separator] == '~'))
        }

    private fun decodeStates(encoded: String): Map<String, String> = encoded.split(";").associate { pair ->
        val separator = pair.indexOfFirst { it == '=' || it == '~' }
        pair.substring(0, separator) to pair.substring(separator + 1)
    }

    private fun stateEvents(): List<LogRecordData> = logExporter.exportedLogs.filter { it.eventName != null }

    private fun exportedSpan(name: String): SpanData = spanExporter.exportedSpans.single { it.name == name }

    private fun endedSpan(name: String): SpanData = endedSpans.single { it.name == name }

    private fun LogRecordData.stateName(): String = attributes["emb.states.name"].toString()

    private fun LogRecordData.newValue(): String? = attributes["emb.states.new_value"]?.toString()

    private fun Log.attribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateAttribute(key: String): String? = attributes?.findAttributeValue(key)

    private fun Span.stateKeys(): List<String> = attributes.orEmpty().mapNotNull { it.key }.filter { it.startsWith("emb.states.") }

    private fun isStateValue(key: String): Boolean = VALUE_KEYS.any(key::startsWith)

    /**
     * The value of every state as of the change numbered [sequence], worked out from the span the change log is delivered
     * as: the values it opened with, then each change up to that one. The changes a span holds are consecutive, so the
     * number of a change is its position after the sequence the initial values are as of.
     */
    private fun Span.valuesAt(sequence: Long): Map<String, String> {
        val initialSequence = checkNotNull(attributes?.findAttributeValue("emb.states.initial_seq")).toLong()
        val values = LinkedHashMap<String, String>()
        checkNotNull(attributes).forEach { attribute ->
            val key = checkNotNull(attribute.key)
            if (key.startsWith("emb.states.initial_value.")) {
                values[key.removePrefix("emb.states.initial_value.")] = checkNotNull(attribute.data)
            }
        }
        checkNotNull(events).take((sequence - initialSequence).toInt()).forEachIndexed { index, event ->
            assertEquals(initialSequence + index + 1, checkNotNull(event.attributes?.findAttributeValue("emb.states.seq")).toLong())
            values[checkNotNull(event.name).removePrefix("emb-state-")] =
                checkNotNull(event.attributes?.findAttributeValue("emb.states.new_value"))
        }
        return values
    }

    private companion object {
        const val ACROSS = "across-a-change"
        const val STEADY = "after-the-change"
        const val AFTER_THE_FACT = "recorded-after-the-fact"
        const val ENDED_EARLIER = "ended-before-the-change"
        const val LEFT_OPEN = "left-open"
        const val BEFORE_LEFT_OPEN = "before-left-open"
        const val AFTER_LEFT_OPEN = "after-left-open"
        const val START_FORGOTTEN = "start-forgotten"
        const val END_FORGOTTEN = "end-forgotten"
        const val LAST_VALUE = "last"
        const val REMEMBERED_CHANGES = 64
        const val LOG_MESSAGE = "a-log"
        const val CHANGE_EVENT = "emb.states.change"
        const val SNAPSHOT_EVENT = "emb.states.snapshot"
        const val VALUE_PREFIX = "emb.states.value."
        const val VALUE_TYPE_PREFIX = "emb.states.value_type."
        val VALUE_KEYS = listOf("emb.states.value", "emb.states.end_value")
        val persistenceWorkers = listOf(Worker.Background.SessionPersistenceWorker, Worker.Background.PeriodicCacheWorker)
    }
}
