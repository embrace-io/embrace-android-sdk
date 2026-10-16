package io.embrace.android.embracesdk.instrumentation.leaks

private const val GC_ATTEMPTS = 20
private const val GC_RETRY_DELAY_MS = 25L

/**
 * Forces real collections until [detector] confirms a suspect of [objectType], and returns its [LeakContext]. [detector]
 * must already be started. [System.gc] is only a request, so this retries a bounded number of times rather than trusting
 * a single call.
 */
internal fun awaitSuspect(detector: LeakDetector, objectType: String): LeakContext {
    repeat(GC_ATTEMPTS) {
        val match = detector.suspects()
            .mapNotNull { it.token as? LeakContext }
            .firstOrNull { it.objectType == objectType }
        if (match != null) return match
        System.gc()
        Thread.sleep(GC_RETRY_DELAY_MS)
    }

    throw AssertionError("no $objectType suspect was confirmed after $GC_ATTEMPTS collections")
}
