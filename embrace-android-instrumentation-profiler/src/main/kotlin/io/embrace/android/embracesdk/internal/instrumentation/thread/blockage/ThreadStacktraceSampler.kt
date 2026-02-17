package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.arch.stacktrace.truncateStacktrace
import io.embrace.android.embracesdk.internal.clock.Clock
import java.util.concurrent.CopyOnWriteArrayList

class ThreadStacktraceSampler(
    private val clock: Clock,
    private val targetThread: Thread,
    private val sampleLimit: Int,
    private val stacktraceFrameLimit: Int,
) {

    private val samples: MutableList<ThreadSampleMetadata> = CopyOnWriteArrayList()

    fun captureSample() {
        if (samples.size < MAX_SAMPLE_COUNT) {
            val sampleTimeMs = clock.now()
            val withinSampleLimit = samples.size < sampleLimit
            val trace = if (withinSampleLimit) {
                truncateStacktrace(
                    targetThread,
                    targetThread.stackTrace,
                    stacktraceFrameLimit
                )
            } else {
                null
            }
            samples.add(
                ThreadSampleMetadata(
                    sample = trace,
                    sampleTimeMs = sampleTimeMs,
                    sampleOverheadMs = sampleTimeMs - clock.now(),
                )
            )
        }
    }

    fun retrieveSampleMetadata() = samples.toList()

    private companion object {

        /**
         * Hard limit for the maximum number of samples to track in one instantiation,
         * even if there's not a stacktrace associated with each sample.
         */
        private const val MAX_SAMPLE_COUNT = 1000
    }
}
