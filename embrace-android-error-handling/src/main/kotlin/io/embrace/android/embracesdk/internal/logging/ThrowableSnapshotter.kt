package io.embrace.android.embracesdk.internal.logging

/**
 * Copies a [Throwable] into an [SdkErrorSnapshot].
 *
 * Any [Throwable] subclass can override its accessors to throw, return null or form cycles, so each one is guarded
 * and replaced with placeholders if it fails. The [Throwable] never has `toString()`, `equals()` or `hashCode()`
 * called on it, and cycles are detected by reference.
 */
internal object ThrowableSnapshotter {

    private const val MAX_CAUSES = 3
    private const val MAX_SUPPRESSED = 3
    private const val MAX_FRAMES = 200
    private const val MAX_VM_ERROR_FRAMES = 32
    private const val MAX_MESSAGE_LENGTH = 1024
    private const val MAX_CLASS_NAME_LENGTH = 256

    /**
     * Snapshots [throwable] as an error of [type] captured at [timestampMs], along with up to 3 causes and 3 exceptions it
     * suppressed.
     */
    fun snapshot(type: SdkErrorType, throwable: Throwable, timestampMs: Long): SdkErrorSnapshot {
        val seen = ArrayList<Throwable>(1 + MAX_CAUSES + MAX_SUPPRESSED)
        seen.add(throwable)

        val chain = ArrayList<ThrowableSnapshot>(1 + MAX_CAUSES)
        chain.add(snapshotOf(throwable))
        var cause = causeOf(throwable)
        while (cause != null && chain.size <= MAX_CAUSES && !seen.containsRef(cause)) {
            seen.add(cause)
            chain.add(snapshotOf(cause))
            cause = causeOf(cause)
        }

        val suppressed = ArrayList<ThrowableSnapshot>(MAX_SUPPRESSED)
        suppressedOf(throwable)?.forEach { exc ->
            if (exc != null && suppressed.size < MAX_SUPPRESSED && !seen.containsRef(exc)) {
                seen.add(exc)
                suppressed.add(snapshotOf(exc))
            }
        }
        return SdkErrorSnapshot(type, timestampMs, chain, suppressed)
    }

    private fun snapshotOf(throwable: Throwable): ThrowableSnapshot {
        val vmError = throwable is VirtualMachineError
        return ThrowableSnapshot(
            className = classNameOf(throwable),
            message = messageOf(throwable),
            frames = framesOf(throwable, if (vmError) MAX_VM_ERROR_FRAMES else MAX_FRAMES),
        )
    }

    private fun classNameOf(throwable: Throwable): String = try {
        throwable.javaClass.name.cap(MAX_CLASS_NAME_LENGTH)
    } catch (_: Throwable) {
        unavailable("class")
    }

    private fun messageOf(throwable: Throwable): String? = try {
        throwable.message?.cap(MAX_MESSAGE_LENGTH)
    } catch (_: Throwable) {
        unavailable("message")
    }

    private fun causeOf(throwable: Throwable): Throwable? = try {
        throwable.cause
    } catch (_: Throwable) {
        null
    }

    private fun suppressedOf(throwable: Throwable): Array<out Throwable?>? = try {
        throwable.suppressed
    } catch (_: Throwable) {
        null
    }

    private fun framesOf(throwable: Throwable, max: Int): List<String> {
        val trace: Array<out StackTraceElement?> = try {
            throwable.stackTrace ?: return emptyList()
        } catch (_: Throwable) {
            return listOf(unavailable("stackTrace"))
        }
        val count = minOf(trace.size, max)
        val frames = ArrayList<String>(count)
        for (k in 0 until count) {
            frames.add(frameOf(trace[k]))
        }
        return frames
    }

    private fun frameOf(element: StackTraceElement?): String = try {
        element?.toString() ?: unavailable("frame")
    } catch (_: Throwable) {
        unavailable("frame")
    }

    private fun List<Throwable>.containsRef(throwable: Throwable): Boolean {
        for (k in indices) {
            if (this[k] === throwable) {
                return true
            }
        }
        return false
    }

    private fun String.cap(max: Int): String = if (length > max) substring(0, max) else this

    private fun unavailable(field: String) = "<unavailable: $field>"
}
