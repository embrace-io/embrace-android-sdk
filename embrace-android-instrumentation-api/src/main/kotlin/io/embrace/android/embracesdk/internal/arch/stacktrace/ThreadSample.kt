package io.embrace.android.embracesdk.internal.arch.stacktrace

data class ThreadSample(
    val threadId: Long,
    val state: Thread.State?,
    val name: String?,
    val priority: Int,
    val lines: List<String>?,
    val frameCount: Int,
)
