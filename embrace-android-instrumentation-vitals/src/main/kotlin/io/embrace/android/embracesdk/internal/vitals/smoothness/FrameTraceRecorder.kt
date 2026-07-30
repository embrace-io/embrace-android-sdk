package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.util.Base64
import androidx.annotation.WorkerThread

/**
 * Records a per-frame duration trace over a focal moment, as a diagnostic aid while smoothness thresholds
 * are being tuned. Only constructed for a sampled fraction of devices (see
 * [io.embrace.android.embracesdk.internal.config.behavior.VitalsBehavior.isSmoothnessFrameTraceEnabled]),
 * so the buffer is pre-allocated once per instance rather than per focal moment.
 *
 * Each frame's total duration (whole milliseconds) is packed as an unsigned LEB128 varint into a
 * fixed-size buffer: a typical 1-3ms frame costs a single byte, while a slow/janky frame costs more,
 * so detail scales with what's actually interesting. Once the buffer fills, further frames in that
 * focal moment are silently dropped rather than corrupting the encoding with a truncated varint.
 */
internal class FrameTraceRecorder(
    capacityBytes: Int = DEFAULT_CAPACITY_BYTES,
) {

    private val buffer = ByteArray(capacityBytes)
    private var writeIndex = 0

    @WorkerThread
    fun onFocalMomentStart() {
        writeIndex = 0
    }

    @WorkerThread
    fun onFocalMomentFrame(frameDurationMs: Long) {
        writeVarInt(frameDurationMs.coerceAtLeast(0L))
        return
    }

    private fun writeVarInt(value: Long) {
        var v = value
        var index = writeIndex
        while (true) {
            if (index >= buffer.size) {
                // Not enough room to finish this varint: drop it rather than write a truncated one.
                return
            }
            val septet = (v and SEPTET_MASK).toByte()
            v = v ushr 7
            if (v == 0L) {
                buffer[index] = septet
                writeIndex = index + 1
                return
            }
            buffer[index] = (septet.toInt() or CONTINUATION_BIT).toByte()
            index++
        }
    }

    /**
     * Base64-encodes the frames recorded since the last [onFocalMomentStart], or null if none were recorded.
     */
    @WorkerThread
    fun toBase64(): String? {
        if (writeIndex == 0) {
            return null
        }
        return Base64.encodeToString(buffer, 0, writeIndex, Base64.NO_PADDING or Base64.NO_WRAP)
    }

    internal companion object {
        const val DEFAULT_CAPACITY_BYTES = 1024
        private const val SEPTET_MASK = 0x7FL
        private const val CONTINUATION_BIT = 0x80
    }
}
