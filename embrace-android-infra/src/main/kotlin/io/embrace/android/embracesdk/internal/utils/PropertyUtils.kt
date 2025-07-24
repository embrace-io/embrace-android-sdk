package io.embrace.android.embracesdk.internal.utils

object PropertyUtils {

    private const val END_CHARS = "..."

    fun truncate(value: String, maxLength: Int): String {
        if (value.length <= maxLength) {
            return value
        }

        return "${value.take(maxLength - 3)}$END_CHARS"
    }
}
