package io.embrace.android.embracesdk.internal.utils

/**
 * Counts the bytes and the writes that one persistence mechanism puts on disk, published as
 * cumulative system trace counters.
 */
class FileWriteCounters(
    bytesCounterName: String,
    filesCounterName: String,
) {

    private val bytes = TraceCounter(bytesCounterName)
    private val files = TraceCounter(filesCounterName)

    /**
     * Records one write of [byteCount] bytes. An append counts as a write of its own.
     */
    fun recordWrite(byteCount: Long) {
        bytes.add(byteCount)
        files.add(1)
    }
}
