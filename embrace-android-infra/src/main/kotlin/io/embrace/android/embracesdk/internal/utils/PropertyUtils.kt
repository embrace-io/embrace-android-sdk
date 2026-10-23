package io.embrace.android.embracesdk.internal.utils

object PropertyUtils {

    private const val END_CHARS = "..."

    fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }
        if (maxLength <= END_CHARS.length) {
            return value.takeWithoutSplitting(maxLength)
        }
        return "${value.takeWithoutSplitting(maxLength - END_CHARS.length)}$END_CHARS"
    }

    private fun String.takeWithoutSplitting(n: Int): String {
        val end = n.coerceAtLeast(0)
        return if (end > 0 && Character.isHighSurrogate(this[end - 1])) take(end - 1) else take(end)
    }
}
