package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.capture.state.StateRecorder
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

/**
 * Compares what capturing states costs in bytes when each state is recorded into a span of its own, against when they are
 * all recorded into the journal of state changes, with a session part's telemetry persisted as multiple files. The tables it
 * measures are printed, and written to [REPORT_DIR] along with an example of the session part each of the two delivers.
 */
internal class StateCapturePayloadSizeTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `journaled states add less to a session part than a span for each state while changes are few`() {
        val rows = TRANSITIONS_PER_STATE.flatMap { transitions ->
            val floor = measure(stateCount = 0, journaled = false, transitions = transitions)
            STATE_COUNTS.flatMap { stateCount ->
                val perState = measure(stateCount, journaled = false, transitions = transitions)
                val journal = measure(stateCount, journaled = true, transitions = transitions)

                // a span per state, each linked from the session part span twice
                assertEquals(stateCount, perState.stateSpans)
                assertEquals(2 * stateCount, perState.sessionSpanLinks - floor.sessionSpanLinks)
                // one span however many states there are, linked once
                assertEquals(1, journal.stateSpans)
                assertEquals(1, journal.sessionSpanLinks - floor.sessionSpanLinks)

                // a change costs the journal's span more than it costs a span of its own, as it has to say which state
                // changed and where in the order, so the journal is only sure to be the smaller while changes are few
                if (stateCount >= SMALLEST_COUNT_THE_JOURNAL_MUST_WIN && transitions <= MOST_TRANSITIONS_THE_JOURNAL_MUST_WIN) {
                    val label = "$stateCount states, $transitions transitions each"
                    assertTrue(label, journal.jsonBytes < perState.jsonBytes)
                    assertTrue(label, journal.gzipBytes < perState.gzipBytes)
                    assertTrue(label, journal.diskBytes < perState.diskBytes)
                }
                listOf(
                    row(stateCount, transitions, "per-state", perState, floor),
                    row(stateCount, transitions, "journal", journal, floor),
                )
            }
        }
        report("payload-size.tsv", PAYLOAD_HEADER, rows)

        File(REPORT_DIR).resolve("example-per-state.json").outputStream().use {
            serialize(delivered(EXAMPLE_STATE_COUNT, journaled = false, EXAMPLE_TRANSITIONS).first, it)
        }
        File(REPORT_DIR).resolve("example-journal.json").outputStream().use {
            serialize(delivered(EXAMPLE_STATE_COUNT, journaled = true, EXAMPLE_TRANSITIONS).first, it)
        }
    }

    @Test
    fun `persisting a change to the journal costs the same however many changes came before it`() {
        val measured = CHANGES_BEFORE.map { changesBefore ->
            val spanBytes: Long
            val changesHeldBySpan: Int
            StateChangePersistenceFixture(journaled = false, changesBefore, tempFolder.newFolder(), steppingClock()).use {
                assertTrue(it.persistOneChange())
                spanBytes = it.persistedBytes()
                changesHeldBySpan = it.changesHeldBySpan()
            }
            val journalBytes: Long
            StateChangePersistenceFixture(journaled = true, changesBefore, tempFolder.newFolder(), steppingClock()).use {
                val before = it.persistedBytes()
                assertTrue(it.persistOneChange())
                journalBytes = it.persistedBytes() - before
            }
            ScalingRow(changesBefore, spanBytes, changesHeldBySpan, journalBytes)
        }

        // every change to the journal is one record: the first also writes the header a file opens with
        val journalBytes = measured.drop(1).map(ScalingRow::journalBytes)
        assertTrue("$journalBytes", journalBytes.max() - journalBytes.min() <= JOURNAL_RECORD_TOLERANCE_BYTES)
        // a span is written again whole, so it grows with every change it holds, until it stops taking them
        val withinSpanLimit = measured.filter { it.changesBefore < SPAN_CHANGE_LIMIT }
        assertEquals(withinSpanLimit.map(ScalingRow::spanBytes).sorted(), withinSpanLimit.map(ScalingRow::spanBytes))
        assertTrue(measured.filter { it.changesBefore >= SPAN_CHANGE_LIMIT }.all { it.changesHeldBySpan == SPAN_CHANGE_LIMIT })

        report(
            "change-scaling.tsv",
            SCALING_HEADER,
            measured.map { listOf(it.changesBefore, it.spanBytes, it.changesHeldBySpan, it.journalBytes).joinToString("\t") },
        )
    }

    /**
     * What a session part carries when its journal holds as many changes as it can, and when more were made than it holds.
     */
    @Test
    fun `a journal at its ceiling delivers that many changes and counts the rest`() {
        val floor = measure(stateCount = 0, journaled = false, transitions = 0)
        val rows = listOf(0, CEILING_OVERSHOOT_PER_STATE).map { overshoot ->
            val (envelope, partDir) = delivered(CEILING_STATE_COUNT, journaled = true, CEILING_TRANSITIONS_PER_STATE + overshoot)
            val stateSpan = envelope.data.spans.orEmpty().single { it.hasAttribute("emb.type", "state") }
            val attributes = stateSpan.attributes.orEmpty().associate { it.key to it.data }
            val dropped = attributes["emb.app.state.dropped_changes"]?.toInt() ?: 0

            assertEquals(StateRecorder.MAX_JOURNAL_CHANGES, stateSpan.events?.size)
            assertEquals(CEILING_STATE_COUNT * overshoot, dropped)
            assertEquals(null, attributes["emb.app.state.truncated"])
            // the values the part ended with are delivered only when the events no longer lead to them
            assertEquals(if (overshoot == 0) 0 else CEILING_STATE_COUNT, attributes.keys.count { it?.startsWith("emb.app.state.final_value.") == true })

            val measurement = measure(envelope, partDir)
            listOf(
                StateRecorder.MAX_JOURNAL_CHANGES + dropped,
                stateSpan.events?.size,
                dropped,
                measurement.jsonBytes - floor.jsonBytes,
                measurement.gzipBytes - floor.gzipBytes,
                measurement.diskBytes - floor.diskBytes,
            ).joinToString("\t")
        }
        report("ceiling.tsv", CEILING_HEADER, rows)
    }

    /**
     * What the number each change carries as `emb.app.state.seq` adds to a session part's change log, by delivering a part and
     * then taking the number off every event. The difference is the number's own bytes, raw as JSON and compressed.
     */
    @Test
    fun `the number on each change adds a few bytes to every event of the change log`() {
        val rows = listOf(10, 100, 1_000).map { transitions ->
            val (envelope, _) = delivered(CEILING_STATE_COUNT, journaled = true, transitions)
            val without = envelope.withoutChangeNumbers()
            val events = envelope.data.spans.orEmpty().single { it.hasAttribute("emb.type", "state") }.events.orEmpty().size
            val withNumbers = Measurement(0, 0, jsonSize(envelope), gzipSize(envelope), 0)
            val withoutNumbers = Measurement(0, 0, jsonSize(without), gzipSize(without), 0)
            assertEquals(CEILING_STATE_COUNT * transitions, events)
            assertTrue(withNumbers.jsonBytes > withoutNumbers.jsonBytes)
            listOf(
                events,
                withoutNumbers.jsonBytes,
                withNumbers.jsonBytes,
                (withNumbers.jsonBytes - withoutNumbers.jsonBytes).toDouble() / events,
                withoutNumbers.gzipBytes,
                withNumbers.gzipBytes,
                (withNumbers.gzipBytes - withoutNumbers.gzipBytes).toDouble() / events,
            ).joinToString("\t")
        }
        report("change-number.tsv", NUMBER_HEADER, rows)
    }

    /**
     * What the stamp on a log adds to the log, in the form Embrace's payload has (one packed attribute and the number) and
     * the form an app's exporters get (one attribute for each state and the number), for the states the fixture has. Both
     * are serialized the way Embrace's payload serializes attributes, so the two are comparable though only the first is
     * ever sent to Embrace.
     */
    @Test
    fun `the stamp on a log adds bytes in each form`() {
        val rows = STATE_COUNTS.map { stateCount ->
            val clock = steppingClock()
            val recorder = StateRecorder(clock)
            repeat(stateCount) { index ->
                recorder.declare(StateDeclaration(StateCaptureFixture.STATE_NAMES[index]))
                recorder.onEvent(
                    StateUpdate(StateCaptureFixture.STATE_NAMES[index], StateCaptureFixture.STATE_VALUES[0], clock.now()),
                )
            }
            val snapshot = recorder.snapshot()
            val base = listOf(Attribute("emb.session_part_id", "A".repeat(UUID_LENGTH)), Attribute("log.record.uid", "B".repeat(UUID_LENGTH)))
            val packed = base + Attribute("emb.app.state.packed", snapshot.encoded) + Attribute("emb.app.state.seq", snapshot.sequence.toString())
            val plain = base + snapshot.attributes.map { Attribute(it.key, it.value) } +
                Attribute("emb.app.state.seq", snapshot.sequence.toString())
            listOf(1, LOGS_IN_A_BATCH).flatMap { logs ->
                val floor = logSize(base, logs)
                val packedSize = logSize(packed, logs)
                val plainSize = logSize(plain, logs)
                listOf(
                    packedSize.first - floor.first,
                    packedSize.second - floor.second,
                    plainSize.first - floor.first,
                    plainSize.second - floor.second,
                )
            }.let { listOf(stateCount) + it }.joinToString("\t")
        }
        report("log-stamp.tsv", LOG_STAMP_HEADER, rows)
    }

    /**
     * The bytes a log adds to a batch of [logs] that differ in their id and time, as JSON and compressed, on average.
     */
    private fun logSize(attributes: List<Attribute>, logs: Int): Pair<Long, Long> {
        val envelope = Envelope(
            version = "0.1.0",
            type = "logs",
            data = LogPayload(
                logs = List(logs) { index ->
                    Log(
                        timeUnixNano = FIXTURE_TIME_MS * NANOS_PER_MS + index * CLOCK_STEP_MS,
                        severityNumber = 9,
                        body = "a log $index",
                        attributes = attributes.map { if (it.key == "log.record.uid") Attribute(it.key, UUID.randomUUID().toString()) else it },
                    )
                },
            ),
        )
        fun write(stream: OutputStream) = EmbraceSerializer().toJson(envelope, Envelope.logEnvelopeSerializer, stream)
        return ByteArrayOutputStream().also(::write).size().toLong() / logs to
            ByteArrayOutputStream().also { write(GZIPOutputStream(it)) }.size().toLong() / logs
    }

    private fun Envelope<SessionPartPayload>.withoutChangeNumbers(): Envelope<SessionPartPayload> = copy(
        data = data.copy(
            spans = data.spans.orEmpty().map { span ->
                if (span.hasAttribute("emb.type", "state")) {
                    span.copy(
                        events = span.events.orEmpty().map { event ->
                            event.copy(attributes = event.attributes.orEmpty().filter { it.key != "emb.app.state.seq" })
                        },
                    )
                } else {
                    span
                }
            },
        ),
    )

    private fun jsonSize(envelope: Envelope<SessionPartPayload>): Long = ByteArrayOutputStream().also { serialize(envelope, it) }.size().toLong()

    private fun gzipSize(envelope: Envelope<SessionPartPayload>): Long =
        ByteArrayOutputStream().also { serialize(envelope, GZIPOutputStream(it)) }.size().toLong()

    /**
     * The benchmarks hold the worker while they measure the thread that reports a change or changes the session part, which
     * is only sound if that thread never waits for the worker.
     */
    @Test(timeout = WAITING_FOR_THE_WORKER_MS)
    fun `nothing a state change or a session part change does waits for the worker`() {
        listOf(false, true).forEach { journaled ->
            StateCaptureFixture(HELD_STATE_COUNT, journaled, tempFolder.newFolder(), steppingClock(), keepParts = true).use {
                it.changeSessionPart()
                it.holdWrites()
                it.transitionEveryState(HELD_TRANSITIONS)
                val stamped = it.stampLog()
                val ended = it.changeSessionPart()
                it.transitionEveryState(1)
                it.releaseWrites()

                val stateSpans = it.readPart(it.partDirectory(ended)).data.spans.orEmpty().filter { span ->
                    span.hasAttribute("emb.type", "state")
                }
                assertEquals(HELD_STATE_COUNT * HELD_TRANSITIONS, stateSpans.sumOf { span -> span.events?.size ?: 0 })
                assertTrue(stamped.isNotEmpty())
            }
        }
    }

    /**
     * Measures a session part in which [stateCount] states each changed value [transitions] times, as it is persisted and
     * as it would be delivered.
     */
    private fun measure(stateCount: Int, journaled: Boolean, transitions: Int): Measurement {
        val (envelope, partDir) = delivered(stateCount, journaled, transitions)
        return measure(envelope, partDir)
    }

    private fun measure(envelope: Envelope<SessionPartPayload>, partDir: File): Measurement {
        val spans = envelope.data.spans.orEmpty()
        val sessionSpan = spans.single { it.hasAttribute("emb.type", "ux.session") }
        return Measurement(
            stateSpans = spans.count { it.hasAttribute("emb.type", "state") },
            sessionSpanLinks = sessionSpan.links?.size ?: 0,
            jsonBytes = ByteArrayOutputStream().also { serialize(envelope, it) }.size().toLong(),
            gzipBytes = ByteArrayOutputStream().also { serialize(envelope, GZIPOutputStream(it)) }.size().toLong(),
            diskBytes = PART_FILES.sumOf { File(partDir, it).length() },
        )
    }

    /**
     * Records a session part in which [stateCount] states each changed value [transitions] times, and returns the envelope
     * read back for it with the directory its files are in. The part measured is the second: the first is where every
     * state reports the value it starts with.
     */
    private fun delivered(
        stateCount: Int,
        journaled: Boolean,
        transitions: Int,
    ): Pair<Envelope<SessionPartPayload>, File> =
        StateCaptureFixture(stateCount, journaled, tempFolder.newFolder(), steppingClock(), keepParts = true).use {
            it.changeSessionPart()
            it.transitionEveryState(transitions)
            val ended = it.changeSessionPart()
            it.readPart(it.partDirectory(ended)) to it.partDir(ended)
        }

    /**
     * A clock whose every reading is later than the last, so that timestamps differ as they would in an app.
     */
    private fun steppingClock(): Clock {
        val now = AtomicLong(FIXTURE_TIME_MS)
        return Clock { now.addAndGet(CLOCK_STEP_MS) }
    }

    private fun serialize(envelope: Envelope<SessionPartPayload>, stream: OutputStream) {
        EmbraceSerializer().toJson(
            Envelope(version = "0.1.0", type = "spans", data = SessionPartPayload(spans = envelope.data.spans)),
            Envelope.sessionEnvelopeSerializer,
            stream,
        )
    }

    private fun Span.hasAttribute(key: String, value: String): Boolean =
        attributes?.any { it.key == key && it.data == value } == true

    /**
     * One line of the report: what [measurement] holds beyond the [floor], which is the session part with no states at all.
     */
    private fun row(stateCount: Int, transitions: Int, recording: String, measurement: Measurement, floor: Measurement): String =
        listOf(
            stateCount,
            transitions,
            recording,
            measurement.stateSpans,
            measurement.sessionSpanLinks - floor.sessionSpanLinks,
            measurement.jsonBytes - floor.jsonBytes,
            measurement.gzipBytes - floor.gzipBytes,
            measurement.diskBytes - floor.diskBytes,
        ).joinToString("\t")

    private fun report(fileName: String, header: String, rows: List<String>) {
        val report = (listOf(header) + rows).joinToString("\n")
        println("$fileName\n$report")
        File(REPORT_DIR).apply { mkdirs() }.resolve(fileName).writeText(report + "\n")
    }

    private class Measurement(
        val stateSpans: Int,
        val sessionSpanLinks: Int,
        val jsonBytes: Long,
        val gzipBytes: Long,
        val diskBytes: Long,
    )

    private class ScalingRow(
        val changesBefore: Int,
        val spanBytes: Long,
        val changesHeldBySpan: Int,
        val journalBytes: Long,
    )

    private companion object {
        const val REPORT_DIR = "build/reports/state-capture"
        const val FIXTURE_TIME_MS = 1_750_000_000_000L
        const val CLOCK_STEP_MS = 137L

        // with one state the two record the same thing, and neither is meant to be smaller
        const val SMALLEST_COUNT_THE_JOURNAL_MUST_WIN = 3
        val STATE_COUNTS = listOf(1, 3, 10, StateCaptureFixture.MAX_STATE_COUNT)
        val TRANSITIONS_PER_STATE = listOf(0, 5, 20, 50, 100)
        const val MOST_TRANSITIONS_THE_JOURNAL_MUST_WIN = 5
        const val EXAMPLE_STATE_COUNT = 3
        const val HELD_STATE_COUNT = 3

        // far longer than the test takes, so that a wait on the held worker fails it and does not hang the build
        const val WAITING_FOR_THE_WORKER_MS = 60_000L

        // 10 states changing 1,000 times each fill a journal exactly
        const val CEILING_STATE_COUNT = 10
        const val CEILING_TRANSITIONS_PER_STATE = StateRecorder.MAX_JOURNAL_CHANGES / CEILING_STATE_COUNT
        const val CEILING_OVERSHOOT_PER_STATE = 100
        const val HELD_TRANSITIONS = 4
        const val EXAMPLE_TRANSITIONS = 1

        val CHANGES_BEFORE = listOf(0, 49, 499, 999, 1_999, 4_999, 9_999)
        const val SPAN_CHANGE_LIMIT = 2_000
        const val JOURNAL_RECORD_TOLERANCE_BYTES = 8L

        val PART_FILES = listOf("completed_spans.pb", "span_snapshots.pb", "state_journal.pb")

        val PAYLOAD_HEADER = listOf(
            "states",
            "transitions_per_state",
            "recording",
            "state_spans",
            "session_span_links",
            "json_bytes",
            "gzip_bytes",
            "disk_bytes",
        ).joinToString("\t")

        val CEILING_HEADER = listOf(
            "changes_made",
            "changes_delivered",
            "changes_dropped",
            "json_bytes",
            "gzip_bytes",
            "disk_bytes",
        ).joinToString("\t")

        const val UUID_LENGTH = 32
        const val LOGS_IN_A_BATCH = 100

        val NUMBER_HEADER = listOf(
            "events",
            "json_bytes_without",
            "json_bytes_with",
            "json_bytes_per_event",
            "gzip_bytes_without",
            "gzip_bytes_with",
            "gzip_bytes_per_event",
        ).joinToString("\t")

        const val NANOS_PER_MS = 1_000_000L

        val LOG_STAMP_HEADER = listOf(
            "states",
            "packed_json_bytes_1_log",
            "packed_gzip_bytes_1_log",
            "plain_json_bytes_1_log",
            "plain_gzip_bytes_1_log",
            "packed_json_bytes_per_log_in_100",
            "packed_gzip_bytes_per_log_in_100",
            "plain_json_bytes_per_log_in_100",
            "plain_gzip_bytes_per_log_in_100",
        ).joinToString("\t")

        val SCALING_HEADER = listOf(
            "changes_before",
            "span_bytes_rewritten",
            "changes_held_by_span",
            "journal_bytes_appended",
        ).joinToString("\t")
    }
}
