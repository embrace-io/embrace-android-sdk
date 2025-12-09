package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.arch.stacktrace.ThreadSample

internal class ThreadSampleMetadata(
    val sample: ThreadSample?,
    val sampleTimeMs: Long,
    val sampleOverheadMs: Long,
)
