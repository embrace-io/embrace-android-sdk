package io.embrace.android.embracesdk.internal.vitals.smoothness

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FrameTraceRecorderTest {

    @Test
    fun `an empty focal moment reports no trace`() {
        val recorder = FrameTraceRecorder()
        recorder.onFocalMomentStart()

        assertNull(recorder.toBase64())
    }

    @Test
    fun `records and round-trips a mix of small and large frame durations`() {
        val recorder = FrameTraceRecorder()
        recorder.onFocalMomentStart()

        val durations = listOf(1L, 3L, 0L, 127L, 128L, 300L, 16_384L)
        durations.forEach(recorder::onFocalMomentFrame)

        assertEquals(durations, decode(recorder.toBase64()))
    }

    @Test
    fun `a negative duration is clamped to zero rather than corrupting the encoding`() {
        val recorder = FrameTraceRecorder()
        recorder.onFocalMomentStart()

        recorder.onFocalMomentFrame(-5L)

        assertEquals(listOf(0L), decode(recorder.toBase64()))
    }

    @Test
    fun `frames that would overflow the buffer are dropped, not truncated`() {
        val recorder = FrameTraceRecorder(capacityBytes = 4)
        recorder.onFocalMomentStart()

        // Each of these needs 2 bytes (>=128); the 3rd would need bytes 5-6, past the 4-byte buffer.
        repeat(3) { recorder.onFocalMomentFrame(200L) }

        // Only the first two 2-byte frames fit; the dropped one leaves no partial/corrupt bytes behind.
        assertEquals(listOf(200L, 200L), decode(recorder.toBase64()))
    }

    @Test
    fun `onFocalMomentStart resets the buffer for a new focal moment`() {
        val recorder = FrameTraceRecorder()
        recorder.onFocalMomentStart()
        recorder.onFocalMomentFrame(50L)
        assertEquals(listOf(50L), decode(recorder.toBase64()))

        recorder.onFocalMomentStart()
        assertNull(recorder.toBase64())

        recorder.onFocalMomentFrame(2L)
        assertEquals(listOf(2L), decode(recorder.toBase64()))
    }

    @Test
    fun `a 1024-byte buffer stays within the internal attribute value length limit once base64-encoded`() {
        val recorder = FrameTraceRecorder()
        recorder.onFocalMomentStart()

        // Fill with worst-case 2-byte durations so the buffer is as full as possible.
        repeat(600) { recorder.onFocalMomentFrame(200L) }

        val base64 = requireNotNull(recorder.toBase64())
        assertTrue("base64 length was ${base64.length}", base64.length <= 2000)
    }

    /** Decodes an unsigned-LEB128-packed byte string back into its per-frame values, for test verification only. */
    private fun decode(base64: String?): List<Long> {
        val bytes = Base64.decode(base64, Base64.NO_PADDING or Base64.NO_WRAP)
        val values = mutableListOf<Long>()
        var value = 0L
        var shift = 0
        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xFF
            value = value or ((unsigned.toLong() and 0x7F) shl shift)
            if (unsigned and 0x80 == 0) {
                values += value
                value = 0L
                shift = 0
            } else {
                shift += 7
            }
        }
        return values
    }
}
