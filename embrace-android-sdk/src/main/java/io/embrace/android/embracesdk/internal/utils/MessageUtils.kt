package io.embrace.android.embracesdk.internal.utils

internal object MessageUtils {

    fun boolToStr(value: Boolean?): String {
        return value?.toString() ?: "null"
    }

    fun withNull(value: Number?): String {
        return when (value) {
            null -> "null"
            else -> "\"" + value + "\""
        }
    }

    fun withNull(value: String?): String {
        return when (value) {
            null -> "null"
            else -> "\"" + value + "\""
        }
    }

    fun withSet(set: Set<String?>?): String {
        if (set.isNullOrEmpty()) {
            return "[]"
        }
        val sb = StringBuilder()
        sb.append("[")
        for (v in set) {
            sb.append(withNull(v))
            sb.append(",")
        }
        if (sb[sb.length - 1] == ',') {
            sb.deleteCharAt(sb.length - 1)
        }
        sb.append("]")
        return sb.toString()
    }

    @JvmStatic
    fun withMap(map: Map<String?, String?>?): String {
        if (map.isNullOrEmpty()) {
            return "{}"
        }
        val sb = StringBuilder()
        sb.append("{")
        for ((key, value) in map) {
            sb.append(withNull(key) + ": " + withNull(value) + ",")
        }
        if (sb[sb.length - 1] == ',') {
            sb.deleteCharAt(sb.length - 1)
        }
        sb.append("}")
        return sb.toString()
    }
}
