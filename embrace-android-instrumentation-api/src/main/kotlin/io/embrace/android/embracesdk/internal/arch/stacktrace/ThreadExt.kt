package io.embrace.android.embracesdk.internal.arch.stacktrace

import android.os.Build
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_STACKTRACE_SIZE_LIMIT
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import kotlin.math.min

fun truncateStacktrace(
    thread: Thread,
    stackTraceElements: Array<StackTraceElement>,
    maxStacktraceSize: Int = DEFAULT_STACKTRACE_SIZE_LIMIT,
): ThreadSample {
    val name = thread.name
    val priority = thread.priority
    val frameCount = stackTraceElements.size
    val lines = stackTraceElements.take(min(MAX_STACKTRACE_SIZE, maxStacktraceSize)).map(StackTraceElement::toString)
    return ThreadSample(thread.compatThreadId(), thread.state, name, priority, lines, frameCount)
}

fun truncateStacktrace(
    thread: Thread,
    stackTraceElements: Array<StackTraceElement>,
): ThreadInfo {
    val stacktrace = truncateStacktrace(thread, stackTraceElements, DEFAULT_STACKTRACE_SIZE_LIMIT)
    return ThreadInfo(
        stacktrace.threadId,
        stacktrace.state,
        stacktrace.name,
        stacktrace.priority,
        stacktrace.lines,
        stacktrace.frameCount,
    )
}

@Suppress("DEPRECATION")
fun Thread.compatThreadId() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
    threadId()
} else {
    id
}

private const val MAX_STACKTRACE_SIZE = 500
