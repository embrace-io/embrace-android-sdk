package io.embrace.android.embracesdk.internal.vitals

/**
 * Dropped/expected frame counts packed as two saturating unsigned 32-bit fields in a single `volatile` compatible value.
 */
@JvmInline
internal value class FrameCounts private constructor(private val packed: Long) {
    private constructor(dropped: Long, expected: Long) : this(
        (dropped.coerceIn(0L, MAX_COUNT) shl Int.SIZE_BITS) or expected.coerceIn(0L, MAX_COUNT),
    )

    val dropped: Long get() = packed ushr Int.SIZE_BITS
    val expected: Long get() = packed and MAX_COUNT

    operator fun plus(other: FrameCounts): FrameCounts {
        return FrameCounts(dropped + other.dropped, expected + other.expected)
    }

    operator fun minus(other: FrameCounts): FrameCounts {
        val newExpected = (expected - other.expected).coerceAtLeast(0L)
        return FrameCounts((dropped - other.dropped).coerceIn(0L, newExpected), newExpected)
    }

    override fun toString(): String {
        return "FrameCounts[dropped=$dropped,expected=$expected]"
    }

    companion object {
        private const val MAX_COUNT: Long = 0xFFFF_FFFFL

        val ZERO: FrameCounts = FrameCounts(0L)

        /**
         * The counts for a single frame spanning [expectedFrames] vsyncs: if [dropped], every vsync but the one it appeared on is dropped.
         */
        fun frame(dropped: Boolean, expectedFrames: Int): FrameCounts {
            val expected = expectedFrames.coerceAtLeast(1).toLong()
            return FrameCounts(if (dropped) expected - 1L else 0L, expected)
        }
    }
}
