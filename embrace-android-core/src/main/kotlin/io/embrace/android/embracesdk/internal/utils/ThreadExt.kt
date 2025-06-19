package io.embrace.android.embracesdk.internal.utils

import android.os.Build
import io.embrace.android.embracesdk.internal.payload.ThreadInfo

fun getThreadInfo(
    thread: Thread,
    stackTraceElements: Array<StackTraceElement>,
    maxStacktraceSize: Int = Integer.MAX_VALUE,
): ThreadInfo {
    val name = thread.name
    val priority = thread.priority
    val frameCount = stackTraceElements.size
    val lines = stackTraceElements.take(maxStacktraceSize).map(StackTraceElement::toString)
    return ThreadInfo(thread.compatThreadId(), thread.state, name, priority, lines, frameCount)
}

@Suppress("DEPRECATION")
fun Thread.compatThreadId() = if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.BAKLAVA)) {
    threadId()
} else {
    id
}
