package io.embrace.android.embracesdk.internal.logs

// this is quite ugly, but I didn't want to use the serializer
private fun Array<StackTraceElement>.toExceptionSchema(): String {
    return "[\"${this.joinToString(separator = "\",\"")}\"]"
}
