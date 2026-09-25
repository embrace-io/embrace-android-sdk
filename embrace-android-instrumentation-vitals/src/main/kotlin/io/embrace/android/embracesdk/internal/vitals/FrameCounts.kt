package io.embrace.android.embracesdk.internal.vitals

/**
 * Dropped/expected frame counts packed into a single `volatile` compatible value.
 */
@JvmInline
internal value class FrameCounts private constructor(private val packed: Long) {
    val dropped: Int get() = (packed ushr Int.SIZE_BITS).toInt()
    val expected: Int get() = packed.toInt()

    operator fun plus(other: FrameCounts): FrameCounts = FrameCounts(packed + other.packed)

    operator fun minus(start: FrameCounts): FrameCounts = FrameCounts(packed - start.packed)

    companion object {
        val ZERO: FrameCounts = FrameCounts(0L)

        /**
         * A frame can't span more vsyncs than this, a minute at 60Hz: one bogus frame duration or deadline mustn't swamp the counts.
         */
        const val MAX_EXPECTED_FRAMES: Long = 60L * 60L

        /**
         * The counts for a single frame spanning [expectedFrames] vsyncs: if [dropped], every vsync but the one it appeared on is dropped.
         */
        fun frame(dropped: Boolean, expectedFrames: Long): FrameCounts {
            val expected = expectedFrames.coerceIn(1L, MAX_EXPECTED_FRAMES)
            val droppedFrames = if (dropped) expected - 1L else 0L
            return FrameCounts((droppedFrames shl Int.SIZE_BITS) + expected)
        }
    }
}
