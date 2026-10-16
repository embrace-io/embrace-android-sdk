package io.embrace.android.embracesdk.instrumentation.leaks

/**
 * Identifies the instrumentation that opened a tracked object - see [LeakDetector.trackOpened].
 *
 * An `Int` underneath, so that a [LeakDetector.Sentinel] holding one can never reach a tracked object. Every owner is
 * declared below, and the constructor is private so that none can be made anywhere else: a new instrumentation adds its
 * own entry here, with an id no other entry uses.
 */
@JvmInline
internal value class TrackingSource private constructor(private val id: Int) {

    companion object {

        /**
         * [LeakDetector] itself, for the confirmation sentinels it allocates internally - never passed to
         * [LeakDetector.trackOpened].
         */
        val DETECTOR = TrackingSource(0)

        /**
         * [ActivityLeakDetectionLifecycleCallbacks].
         */
        val ACTIVITY = TrackingSource(1)

        /**
         * [FragmentLeakDetectionLifecycleCallbacks].
         */
        val FRAGMENT = TrackingSource(2)
    }
}
