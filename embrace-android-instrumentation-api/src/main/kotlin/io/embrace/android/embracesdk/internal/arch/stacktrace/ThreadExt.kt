package io.embrace.android.embracesdk.internal.arch.stacktrace

import android.os.Build
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_STACKTRACE_SIZE_LIMIT
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import kotlin.math.min

fun getThreadInfo(
    thread: Thread,
    stackTraceElements: Array<StackTraceElement>,
    maxStacktraceSize: Int = DEFAULT_STACKTRACE_SIZE_LIMIT,
): ThreadInfo {
    val name = thread.name
    val priority = thread.priority
    val frameCount = stackTraceElements.size
    val lines = stackTraceElements.take(min(MAX_STACKTRACE_SIZE, maxStacktraceSize)).map(StackTraceElement::toString)
    return ThreadInfo(thread.compatThreadId(), thread.state, name, priority, lines, frameCount)
}

@Suppress("DEPRECATION")
fun Thread.compatThreadId() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    threadId()
} else {
    id
}

private const val MAX_STACKTRACE_SIZE = 500
