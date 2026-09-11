package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.benchmark.SessionPayloadSizeTest.PersistenceMethod.MULTI_FILE
import io.embrace.android.embracesdk.benchmark.SessionPayloadSizeTest.PersistenceMethod.SINGLE_FILE
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.persistence.CompletedSpansWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionManifestWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionMetadataWriter
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.session.persistence.SessionSpanWriter
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.math.abs

/**
 * Guards what a session costs on disk, for each session size in each of the two persistence
 * formats.
 */
@RunWith(Parameterized::class)
internal class SessionPayloadSizeTest(
    private val size: SessionSize,
    private val persistenceMethod: PersistenceMethod,
) {

    enum class SessionSize(val completedSpanCount: Int) {
        SMALL(10),
        MEDIUM(100),
        LARGE(1000),
    }

    enum class PersistenceMethod { SINGLE_FILE, MULTI_FILE }

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private val fixture by lazy { SimpleSessionFixture(size.completedSpanCount) }

    @Test
    fun `the session is the expected size on disk`() {
        val measured = when (persistenceMethod) {
            MULTI_FILE -> measureSessionPart()
            SINGLE_FILE -> measureSingleFile()
        }

        val changed = measured.filterNot { (name, size) -> size matches expected(name) }
        if (changed.isNotEmpty()) {
            fail(
                changed.entries.joinToString(
                    prefix = "$size $persistenceMethod payload size changed:\n",
                    separator = "\n",
                ) { (name, bytes) -> "  $name was $bytes, expected ${expected(name)}" } +
                    measured.entries.joinToString(
                        prefix = "\n\nIf that is expected, update these baselines to:\n",
                        separator = "\n",
                    ) { (name, bytes) -> """  "$size $name" to ${bytes}L,""" },
            )
        }
    }

    /**
     * Persists the fixture through the production writers, so the files measured are the ones the
     * SDK would actually leave behind.
     */
    private fun measureSessionPart(): Map<String, Long> {
        val logger = FakeInternalLogger(throwOnInternalError = true)
        val sessionsDir = tempFolder.newFolder("embrace_sessions")
        val partDir = File(sessionsDir, fixture.directory.dirName).apply { mkdirs() }
        val target = SessionPartWriteTarget(lazy { sessionsDir }) { fixture.directory }

        SessionManifestWriter(target, logger).write(
            resource = fixture.resource,
            envelopeVersion = fixture.envelopeVersion,
            envelopeType = fixture.envelopeType,
        )
        SessionMetadataWriter(target, { fixture.metadata }, { fixture.resource }, logger).write()
        SessionSpanWriter(target, logger).write(fixture.sessionSpan)
        SpanSnapshotsWriter(target, logger).write(fixture.spanSnapshots)
        with(CompletedSpansWriter(target, logger)) {
            write(fixture.completedSpans)
            close()
        }
        assertEquals(PART_FILE_NAMES.sorted(), partDir.list()?.sorted())
        val sizes = PART_FILE_NAMES.associateWith { size(File(partDir, it)) }
        return sizes + (MULTI_FILE_BLOCKS to sizes.values.sumOf(::blockCost))
    }

    private fun measureSingleFile(): Map<String, Long> {
        val file = File(tempFolder.root, SINGLE_FILE_NAME).apply {
            EmbraceSerializer().toJson(fixture.envelope, Envelope.sessionEnvelopeSerializer, GZIPOutputStream(outputStream()))
        }
        val bytes = size(file)
        return mapOf(SINGLE_FILE_NAME to bytes, SINGLE_FILE_BLOCKS to blockCost(bytes))
    }

    private fun expected(name: String): Long = EXPECTED.getValue("$size $name")

    private fun size(file: File): Long {
        assertTrue("${file.name} was not written", file.isFile)
        return file.length()
    }

    /**
     * The space a file of [bytes] takes up on a filesystem that allocates 4Kb blocks.
     */
    private fun blockCost(bytes: Long): Long = ((bytes + BLOCK_BYTES - 1) / BLOCK_BYTES) * BLOCK_BYTES

    private infix fun Long.matches(expected: Long): Boolean =
        abs(this - expected) <= maxOf(expected / 100, MIN_TOLERANCE_BYTES)

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0} session, {1}")
        fun parameters(): List<Array<Any>> = SessionSize.entries.flatMap { size ->
            PersistenceMethod.entries.map { format -> arrayOf(size, format) }
        }

        private const val BLOCK_BYTES = 4096L
        private const val MIN_TOLERANCE_BYTES = 32L
        private const val SINGLE_FILE_NAME = "session.json.gz"
        private const val MULTI_FILE_BLOCKS = "multi-file blocks"
        private const val SINGLE_FILE_BLOCKS = "single-file blocks"

        private val PART_FILE_NAMES = listOf(
            "manifest.pb",
            "metadata.pb",
            "session_span.pb",
            "span_snapshots.pb",
            "completed_spans.pb",
        )

        private val EXPECTED = mapOf(
            "SMALL manifest.pb" to 279L,
            "SMALL metadata.pb" to 119L,
            "SMALL session_span.pb" to 187L,
            "SMALL span_snapshots.pb" to 798L,
            "SMALL completed_spans.pb" to 4510L,
            "SMALL multi-file blocks" to 24576L,
            "SMALL session.json.gz" to 1570L,
            "SMALL single-file blocks" to 4096L,

            "MEDIUM manifest.pb" to 279L,
            "MEDIUM metadata.pb" to 119L,
            "MEDIUM session_span.pb" to 187L,
            "MEDIUM span_snapshots.pb" to 798L,
            "MEDIUM completed_spans.pb" to 45820L,
            "MEDIUM multi-file blocks" to 65536L,
            "MEDIUM session.json.gz" to 6989L,
            "MEDIUM single-file blocks" to 8192L,

            "LARGE manifest.pb" to 279L,
            "LARGE metadata.pb" to 119L,
            "LARGE session_span.pb" to 187L,
            "LARGE span_snapshots.pb" to 798L,
            "LARGE completed_spans.pb" to 466120L,
            "LARGE multi-file blocks" to 483328L,
            "LARGE session.json.gz" to 59507L,
            "LARGE single-file blocks" to 61440L,
        )
    }
}
