package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.benchmark.NaiveOtelStateFixture.ChangeEventContent
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
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
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread

/**
 * Measures what recording states costs in bytes the way OpenTelemetry's Android SDK does it, with a holder for each state, a
 * stamp on every span and log, and a log for every change, always as the difference from the same telemetry with no states.
 * The tables it measures are printed, and written to [REPORT_DIR].
 */
internal class NaiveOtelStatePayloadSizeTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `stamping a span adds one attribute for each state`() {
        val rows = SPAN_COUNTS.flatMap { spans ->
            val floor = recordPart(stateCount = 0, spans = spans)
            assertTrue(floor.envelope.data.spans.orEmpty().none { it.stateAttributes().isNotEmpty() })

            STATE_COUNTS.flatMap { stateCount ->
                val stamped = recordPart(stateCount, spans)
                val ordinary = stamped.envelope.data.spans.orEmpty().filter {
                    it.name?.endsWith(NaiveOtelStateFixture.ORDINARY_SPAN_NAME) == true
                }
                assertEquals(spans, ordinary.size)
                // every state's holder held its second value when each span started
                val expected = (0 until stateCount).associate {
                    NaiveOtelStateFixture.stateAttribute(it) to StateCaptureFixture.STATE_VALUES[1]
                }
                ordinary.forEach { assertEquals(expected, it.stateAttributes()) }

                // the same spans when a state changes before each of them, so that no two carry the same stamp
                val changing = recordPart(stateCount, spans, changeBeforeEach = true)
                changing.envelope.data.spans.orEmpty()
                    .filter { it.name?.endsWith(NaiveOtelStateFixture.ORDINARY_SPAN_NAME) == true }
                    .forEach { assertEquals(stateCount, it.stateAttributes().size) }

                listOf(VALUES_CONSTANT to stamped, VALUES_CHANGING to changing).map { (values, recorded) ->
                    val added = recorded.measurement - floor.measurement
                    listOf(stateCount, spans, values) + added.values() + added.values().map { it.perUnit(spans) }
                }
            }.map { it.joinToString("\t") }
        }
        report("naive-span-stamp.tsv", SPAN_STAMP_HEADER, rows)
    }

    @Test
    fun `stamping a log adds one attribute for each state`() {
        val rows = LOG_COUNTS.flatMap { logs ->
            val floor = recordLogs(stateCount = 0, logs = logs)
            assertTrue(floor.none { it.stateAttributes().isNotEmpty() })

            STATE_COUNTS.map { stateCount ->
                val stamped = recordLogs(stateCount, logs)
                assertEquals(logs, stamped.size)
                val expected = (0 until stateCount).associate {
                    NaiveOtelStateFixture.stateAttribute(it) to StateCaptureFixture.STATE_VALUES[1]
                }
                stamped.forEach { assertEquals(expected, it.stateAttributes()) }

                val changing = recordLogs(stateCount, logs, changeBeforeEach = true)
                assertEquals(logs, changing.size)

                listOf(VALUES_CONSTANT to stamped, VALUES_CHANGING to changing).map { (values, recorded) ->
                    val added = logMeasurement(recorded) - logMeasurement(floor)
                    (listOf(stateCount, logs, values) + added.wire() + added.wire().map { it.perUnit(logs) }).joinToString("\t")
                }
            }.flatten()
        }
        report("naive-log-stamp.tsv", LOG_STAMP_HEADER, rows)
    }

    @Test
    fun `a change event is a log for each change`() {
        val floor = logMeasurement(emptyList())
        val rows = STATE_COUNTS.flatMap { stateCount ->
            CHANGE_COUNTS.flatMap { changes ->
                ChangeEventContent.values().map { content ->
                    val events = NaiveOtelStateFixture(stateCount, tempFolder.newFolder(), steppingClock(), content).use {
                        it.transitionEveryState(changes)
                        it.logs()
                    }
                    assertEquals(stateCount * changes, events.count(Log::isChangeEvent))
                    // a change event says which state changed, to what, and with every value only when it is asked to
                    events.forEach {
                        assertEquals(if (content == ChangeEventContent.EVERY_VALUE) stateCount else 0, it.stateAttributes().size)
                    }

                    val added = logMeasurement(events) - floor
                    val perChange = added.wire().map { it.perUnit(stateCount * changes) }
                    (listOf(stateCount, changes, content, events.size) + added.wire() + perChange).joinToString("\t")
                }
            }
        }
        report("naive-change-event.tsv", CHANGE_EVENT_HEADER, rows)
    }

    @Test
    fun `whole scenarios add what the naive model adds beside what the recordings of today add`() {
        val existing = mutableMapOf<Pair<Int, Int>, List<Measurement>>()
        val rows = STATE_COUNTS_IN_SCENARIOS.flatMap { stateCount ->
            SCENARIO_CHANGES.flatMap { changes ->
                val (perState, journal) = existing.getOrPut(stateCount to changes) {
                    listOf(false, true).map { existingAdded(stateCount, journaled = it, changes) }
                }
                SCENARIO_SPANS.flatMap { spans ->
                    SCENARIO_LOGS.map { logs ->
                        val naive = scenario(stateCount, changes, spans, logs)
                        val floor = scenario(stateCount = 0, changes, spans, logs)
                        val session = naive.part.measurement - floor.part.measurement
                        val logEnvelope = naive.logs - floor.logs
                        val total = session + logEnvelope

                        listOf(stateCount, changes, spans, logs).plus(session.wire())
                            .plus(logEnvelope.wire()).plus(total.wire())
                            .plus(perState.wire()).plus(journal.wire())
                            .joinToString("\t")
                    }
                }
            }
        }
        report("naive-scenario.tsv", SCENARIO_HEADER, rows)
    }

    @Test
    fun `stateless naive recording is the floor`() {
        val naive = recordPart(stateCount = 0, spans = 0)
        val today = StateCaptureFixture(0, journaled = false, tempFolder.newFolder(), steppingClock(), keepParts = true).use {
            it.changeSessionPart()
            val ended = it.changeSessionPart()
            val envelope = it.readPart(it.partDirectory(ended))
            Recorded(envelope, it.partDir(ended))
        }
        // gzip is left out: it differs by a few bytes between two runs, as the ids that are random compress differently
        assertEquals(today.measurement.jsonBytes, naive.measurement.jsonBytes)
        assertEquals(today.measurement.diskBytes, naive.measurement.diskBytes)
    }

    /**
     * Four threads each change a state of their own as fast as they can. Nothing orders the changes but the clock each of
     * them reads before it emits, so the table says how often the order the events were emitted in disagrees with it.
     */
    @Test
    fun `changes from threads at once are recorded with timestamps that tie or run backwards`() {
        val rows = (1..ORDER_RUNS).map { run ->
            val events = NaiveOtelStateFixture(ORDER_THREADS, tempFolder.newFolder(), realClock()).use { fixture ->
                val start = CountDownLatch(1)
                val threads = List(ORDER_THREADS) { index ->
                    thread {
                        start.await()
                        repeat(ORDER_CHANGES_PER_THREAD) { fixture.transition(index) }
                    }
                }
                start.countDown()
                threads.forEach(Thread::join)
                fixture.logs().filter(Log::isChangeEvent)
            }
            assertEquals(ORDER_THREADS * ORDER_CHANGES_PER_THREAD, events.size)

            val pairs = events.map { checkNotNull(it.timeUnixNano) }.zipWithNext()
            listOf(
                run,
                ORDER_THREADS,
                ORDER_CHANGES_PER_THREAD,
                events.size,
                pairs.size,
                pairs.count { (before, after) -> after == before },
                pairs.count { (before, after) -> after < before },
            ).joinToString("\t")
        }
        report("naive-order.tsv", ORDER_HEADER, rows)
    }

    /**
     * Records a session part in which [stateCount] states each changed once and then [spans] ordinary spans were recorded,
     * and returns what is delivered for the part. The part is the second: the first is where nothing is recorded. With
     * [changeBeforeEach], one state changes before every span, each state in turn, so that consecutive spans are stamped
     * with different values.
     */
    private fun recordPart(stateCount: Int, spans: Int, changeBeforeEach: Boolean = false): Recorded =
        NaiveOtelStateFixture(stateCount, tempFolder.newFolder(), steppingClock(), keepParts = true).use {
            it.changeSessionPart()
            it.transitionEveryState(1)
            repeat(spans) { index ->
                if (changeBeforeEach && stateCount > 0) {
                    it.transition(index % stateCount)
                }
                it.recordSpan()
            }
            val ended = it.changeSessionPart()
            Recorded(it.readPart(it.partDirectory(ended)), it.partDir(ended))
        }

    /**
     * Records [logs] ordinary logs after [stateCount] states each changed once, and returns them as the log sink holds them.
     * With [changeBeforeEach], one state changes before every log, each state in turn.
     */
    private fun recordLogs(stateCount: Int, logs: Int, changeBeforeEach: Boolean = false): List<Log> =
        NaiveOtelStateFixture(stateCount, tempFolder.newFolder(), steppingClock()).use {
            it.transitionEveryState(1)
            repeat(logs) { index ->
                if (changeBeforeEach && stateCount > 0) {
                    it.transition(index % stateCount)
                }
                it.recordLog()
            }
            it.logs().filterNot(Log::isChangeEvent)
        }

    /**
     * Records a session part in which [stateCount] states each changed [changes] times while [spans] ordinary spans and
     * [logs] ordinary logs were recorded, and returns what is delivered for the part and for the logs. The changes, the
     * spans and the logs are spread evenly through the part, as they are in an app, so that what a span or a log is stamped
     * with depends on where in the part it was made. A change event carries every state's value, as it does when it passes
     * through the processors that stamp every other log.
     */
    private fun scenario(stateCount: Int, changes: Int, spans: Int, logs: Int): Scenario =
        NaiveOtelStateFixture(
            stateCount,
            tempFolder.newFolder(),
            steppingClock(),
            ChangeEventContent.EVERY_VALUE,
            keepParts = true,
        ).use {
            it.changeSessionPart()
            val totals = intArrayOf(stateCount * changes, spans, logs)
            val done = IntArray(totals.size)
            repeat(totals.sum()) { _ ->
                // whichever of the three is furthest behind its share of the part goes next
                val next = totals.indices.filter { kind -> done[kind] < totals[kind] }
                    .minByOrNull { kind -> done[kind].toDouble() / totals[kind] }
                when (checkNotNull(next)) {
                    CHANGES -> it.transition(done[CHANGES] % stateCount)
                    SPANS -> it.recordSpan()
                    else -> it.recordLog()
                }
                done[next]++
            }
            val ended = it.changeSessionPart()
            Scenario(Recorded(it.readPart(it.partDirectory(ended)), it.partDir(ended)), logMeasurement(it.logs()))
        }

    /**
     * What the recording of today, a span for each state or the journal, adds to the session part in which [stateCount]
     * states each changed [changes] times. It records no ordinary spans and no logs.
     */
    private fun existingAdded(stateCount: Int, journaled: Boolean, changes: Int): Measurement {
        fun record(states: Int): Measurement =
            StateCaptureFixture(states, journaled, tempFolder.newFolder(), steppingClock(), keepParts = true).use {
                it.changeSessionPart()
                it.transitionEveryState(changes)
                val ended = it.changeSessionPart()
                Recorded(it.readPart(it.partDirectory(ended)), it.partDir(ended)).measurement
            }
        return record(stateCount) - record(0)
    }

    private fun logMeasurement(logs: List<Log>): Measurement {
        val envelope = NaiveOtelStateFixture.logEnvelope(logs)
        return measure(diskBytes = 0) { EmbraceSerializer().toJson(envelope, Envelope.logEnvelopeSerializer, it) }
    }

    private val Recorded.measurement: Measurement
        get() = measure(PART_FILES.sumOf { File(partDir, it).length() }) {
            EmbraceSerializer().toJson(
                Envelope(version = "0.1.0", type = "spans", data = SessionPartPayload(spans = envelope.data.spans)),
                Envelope.sessionEnvelopeSerializer,
                it,
            )
        }

    /**
     * Measures what [write] writes, as it is and gzipped.
     */
    private fun measure(diskBytes: Long, write: (OutputStream) -> Unit): Measurement = Measurement(
        jsonBytes = ByteArrayOutputStream().also(write).size().toLong(),
        gzipBytes = ByteArrayOutputStream().also { write(GZIPOutputStream(it)) }.size().toLong(),
        diskBytes = diskBytes,
    )

    /**
     * A clock whose every reading is later than the last, so that timestamps differ as they would in an app.
     */
    private fun steppingClock(): Clock {
        val now = AtomicLong(FIXTURE_TIME_MS)
        return Clock { now.addAndGet(CLOCK_STEP_MS) }
    }

    /**
     * The SDK's clock reading the time that passes, which a unit test of the JVM cannot get from [android.os.SystemClock].
     */
    private fun realClock(): Clock = NormalizedIntervalClock(monotonicClock = { System.nanoTime() / NANOS_PER_MILLI })

    private fun report(fileName: String, header: String, rows: List<String>) {
        val report = (listOf(header) + rows).joinToString("\n")
        println("$fileName\n$report")
        File(REPORT_DIR).apply { mkdirs() }.resolve(fileName).writeText(report + "\n")
    }

    private fun Long.perUnit(units: Int): String = "%.2f".format(Locale.ROOT, toDouble() / units)

    private fun Span.stateAttributes(): Map<String, String?> = attributes.orEmpty().stateAttributes()

    private fun Log.stateAttributes(): Map<String, String?> = attributes.orEmpty().stateAttributes()

    private fun List<Attribute>.stateAttributes(): Map<String, String?> =
        filter { it.key?.startsWith(NaiveOtelStateFixture.STATE_ATTRIBUTE_PREFIX) == true }
            .filterNot { it.key == NaiveOtelStateFixture.STATE_NAME_ATTRIBUTE || it.key == NaiveOtelStateFixture.NEW_VALUE_ATTRIBUTE }
            .associate { checkNotNull(it.key) to it.data }

    /**
     * The bytes a session part or a log envelope takes as JSON, as gzipped JSON and, for a part, on disk.
     */
    private data class Measurement(val jsonBytes: Long, val gzipBytes: Long, val diskBytes: Long) {
        operator fun minus(other: Measurement) =
            Measurement(jsonBytes - other.jsonBytes, gzipBytes - other.gzipBytes, diskBytes - other.diskBytes)

        operator fun plus(other: Measurement) =
            Measurement(jsonBytes + other.jsonBytes, gzipBytes + other.gzipBytes, diskBytes + other.diskBytes)

        fun values(): List<Long> = listOf(jsonBytes, gzipBytes, diskBytes)

        /**
         * What is sent, which is not what is stored.
         */
        fun wire(): List<Long> = listOf(jsonBytes, gzipBytes)
    }

    /**
     * A session part as it is delivered, with the directory its files are in.
     */
    private class Recorded(val envelope: Envelope<SessionPartPayload>, val partDir: File)

    private class Scenario(val part: Recorded, val logs: Measurement)

    private companion object {
        const val REPORT_DIR = "build/reports/state-capture"
        const val FIXTURE_TIME_MS = 1_750_000_000_000L
        const val CLOCK_STEP_MS = 137L
        const val NANOS_PER_MILLI = 1_000_000L

        val STATE_COUNTS = listOf(1, 3, 10, StateCaptureFixture.MAX_STATE_COUNT)
        val SPAN_COUNTS = listOf(1, 10, 100, 500)
        val LOG_COUNTS = listOf(1, 5, 50)
        val CHANGE_COUNTS = listOf(1, 5, 20, 100)

        val STATE_COUNTS_IN_SCENARIOS = listOf(3, 10)
        val SCENARIO_CHANGES = listOf(5, 100)
        val SCENARIO_SPANS = listOf(10, 100, 500)
        val SCENARIO_LOGS = listOf(10, 100)

        // the kinds of thing a scenario spreads through a session part
        const val CHANGES = 0
        const val SPANS = 1

        // whether the states kept their values while the telemetry measured was recorded, or one changed before each record
        const val VALUES_CONSTANT = "constant"
        const val VALUES_CHANGING = "changing"

        const val ORDER_RUNS = 3
        const val ORDER_THREADS = 4
        const val ORDER_CHANGES_PER_THREAD = 2_000

        val PART_FILES = listOf("completed_spans.pb", "span_snapshots.pb", "state_journal.pb")

        val SPAN_STAMP_HEADER = listOf(
            "states",
            "spans_in_part",
            "values",
            "json_bytes_added",
            "gzip_bytes_added",
            "disk_bytes_added",
            "json_bytes_added_per_span",
            "gzip_bytes_added_per_span",
            "disk_bytes_added_per_span",
        ).joinToString("\t")

        val LOG_STAMP_HEADER = listOf(
            "states",
            "logs_in_batch",
            "values",
            "json_bytes_added",
            "gzip_bytes_added",
            "json_bytes_added_per_log",
            "gzip_bytes_added_per_log",
        ).joinToString("\t")

        val CHANGE_EVENT_HEADER = listOf(
            "states",
            "changes_of_each_state",
            "event_content",
            "events",
            "log_envelope_json_bytes_added",
            "log_envelope_gzip_bytes_added",
            "json_bytes_added_per_change",
            "gzip_bytes_added_per_change",
        ).joinToString("\t")

        val SCENARIO_HEADER = listOf(
            "states",
            "changes_of_each_state",
            "spans_in_part",
            "logs",
            "naive_session_envelope_json_added",
            "naive_session_envelope_gzip_added",
            "naive_log_envelope_json_added",
            "naive_log_envelope_gzip_added",
            "naive_total_json_added",
            "naive_total_gzip_added",
            "span_per_state_session_envelope_only_no_spans_no_logs_json_added",
            "span_per_state_session_envelope_only_no_spans_no_logs_gzip_added",
            "journal_session_envelope_only_no_spans_no_logs_json_added",
            "journal_session_envelope_only_no_spans_no_logs_gzip_added",
        ).joinToString("\t")

        val ORDER_HEADER = listOf(
            "run",
            "threads",
            "changes_per_thread",
            "events_emitted",
            "consecutive_pairs",
            "pairs_with_equal_timestamp",
            "pairs_with_earlier_timestamp",
        ).joinToString("\t")
    }
}

private fun Log.isChangeEvent(): Boolean = attributes?.any { it.key == "emb.type" && it.data == "state" } == true
