package io.embrace.android.embracesdk.internal.perfetto

import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEvent
import io.embrace.android.embracesdk.internal.perfetto.proto.FtraceEventBundle
import io.embrace.android.embracesdk.internal.perfetto.proto.Trace
import io.embrace.android.embracesdk.internal.perfetto.proto.TracePacket
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Decodes a gzipped perfetto trace onto the wire model generated from `trace.proto`.
 *
 * Throws [IOException] when the file is not a trace this can decode
 */
internal fun parseTrace(trace: File): Trace = try {
    GZIPInputStream(trace.inputStream()).source().buffer().use(Trace.ADAPTER::decode)
} catch (exc: IOException) {
    throw IOException("could not read ${trace.path} as a perfetto trace: ${exc.message}", exc)
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
