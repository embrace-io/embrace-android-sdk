package io.embrace.android.embracesdk.internal.perfetto.trace

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.ProcessTree
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
import okio.buffer
import okio.source
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

private const val MAGIC_BYTES = 2

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4b)
private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())

/**
 * Decodes a perfetto trace onto the wire model generated from `trace.proto`.
 *
 * Throws [IOException] when the file is not a trace this can decode
 */
internal fun parseTrace(trace: File): Trace = try {
    traceStream(trace).source().buffer().use(Trace.ADAPTER::decode)
} catch (exc: IOException) {
    throw IOException("could not read ${trace.path} as a perfetto trace: ${exc.message}", exc)
}

/**
 * Opens [trace] for decoding, unwrapping whichever container it arrived in.
 *
 * A trace pulled off a device by androidx.benchmark is a zip holding `Trace_output.pb`, one unpacked and
 * recompressed by hand is a gzip, and one merely unpacked is the bare protobuf.
 *
 * Throws [IOException] when the file cannot be opened or is invalid.
 */
internal fun traceStream(trace: File): InputStream {
    val stream = BufferedInputStream(trace.inputStream())
    stream.mark(MAGIC_BYTES)
    val magic = stream.readNBytes(MAGIC_BYTES)
    stream.reset()
    return when {
        magic.contentEquals(ZIP_MAGIC) -> bundleStream(trace, stream)
        magic.contentEquals(GZIP_MAGIC) -> GZIPInputStream(stream)
        else -> stream
    }
}

/**
 * Advances a bundle to the trace inside it, which is its first file. The entry is normally named
 * `Trace_output.pb`.
 */
private fun bundleStream(trace: File, stream: InputStream): InputStream {
    val zip = ZipInputStream(stream)
    var entry = zip.nextEntry
    while (entry != null && entry.isDirectory) {
        entry = zip.nextEntry
    }
    if (entry == null) {
        zip.close()
        throw IOException("${trace.path} is a zip holding no trace")
    }
    return zip
}

/** Flattens a trace to the ftrace events its bundles carry, in the order the trace holds them. */
internal fun ftraceEvents(trace: Trace): List<FtraceEvent> = trace.packet
    .mapNotNull(TracePacket::ftrace_events)
    .flatMap(FtraceEventBundle::event)

/**
 * The atrace events among [events]. Ftrace events of any other type never carry a `print`, so this
 * is what atrace wrote and nothing else.
 */
internal fun printEvents(events: List<FtraceEvent>): List<FtraceEvent> = events.filter { it.print != null }

/**
 * Names the threads a trace recorded, taken from the process stats captured alongside atrace.
 *
 * A process's main thread has no entry of its own, since the kernel names it after the process, so
 * it takes the process name instead. Every other name comes from the thread table.
 */
internal fun threadNames(trace: Trace): Map<Int, String> {
    val trees = trace.packet.mapNotNull(TracePacket::process_tree)
    val processes = trees.flatMap(ProcessTree::processes)
        .mapNotNull { process -> process.cmdline.firstOrNull()?.let { process.pid to it } }
    val threads = trees.flatMap(ProcessTree::threads)
        .mapNotNull { thread -> thread.name.takeIf(String::isNotEmpty)?.let { thread.tid to it } }
    return (processes + threads).toMap()
}

/**
 * The wall clock window the capture covers, from its first ftrace event to its last. This is an
 * approximation and gives a ballpark figure only.
 *
 * This spans every ftrace event, whether or not atrace wrote it, so time the trace recorded with no
 * instrumented section running on it still counts. Zero when the trace holds no events.
 */
internal fun traceWindowNanos(events: List<FtraceEvent>): Long = when {
    events.isEmpty() -> 0L
    else -> events.maxOf(FtraceEvent::timestamp) - events.minOf(FtraceEvent::timestamp)
}

/**
 * The instant the capture begins, which every counter reading is an offset from. Zero when the trace
 * holds no events.
 */
internal fun traceStartNanos(events: List<FtraceEvent>): Long =
    events.minOfOrNull(FtraceEvent::timestamp) ?: 0L
