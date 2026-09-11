package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Reads the atrace slices out of a gzipped perfetto trace.
 *
 * Throws [IOException] when the file is not a trace this can read, rather than returning an empty
 * result: a trace holding no slices and a trace that could not be parsed are different answers.
 */
internal fun parseTrace(trace: File): PerfettoTrace = buildSlices(readAtraceEvents(decodeTrace(trace)))

/**
 * The whole trace is decoded in one pass, which holds it in memory along with the unknown fields
 * Wire keeps so that a message can be re-encoded. That is a few packets' worth for the config the
 * macrobenchmark uses. If traces ever grow enough for it to matter, decode a packet at a time with
 * `com.squareup.wire.ProtoReader`, as `CompletedSpansReader` in embrace-android-session-persistence
 * does.
 */
private fun decodeTrace(trace: File): Trace = try {
    GZIPInputStream(trace.inputStream()).source().buffer().use(Trace.ADAPTER::decode)
} catch (exc: IOException) {
    throw IOException("could not read ${trace.path} as a perfetto trace: ${exc.message}", exc)
}

/**
 * Flattens a trace to its atrace print events. Ftrace events of any other type are not atrace
 * events at all, so they are dropped rather than counted as ignored.
 */
internal fun readAtraceEvents(trace: Trace): List<AtraceEvent> = trace.packet
    .mapNotNull(TracePacket::ftrace_events)
    .flatMap(FtraceEventBundle::event)
    .mapNotNull { event ->
        event.print?.let { AtraceEvent(event.pid, event.timestamp, it.buf) }
    }
