package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.annotation.ObservedActivity
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.opentelemetry.sdk.common.Clock

/**
 * Creates [ActivityLifecycleListener] that maps Activity lifecycle events to the given [UiLoadEventListener].
 * It will create an implementation that uses the most suitable API given the OS version.
 *
 * For details of how these events are used to create UI Load traces, see [UiLoadTraceEmitter] for details.
 */
fun createActivityLoadEventEmitter(
    uiLoadEventListener: UiLoadEventListener,
    clock: Clock,
    versionChecker: VersionChecker,
): ActivityLifecycleListener {
    val uiLoadEventEmitter = UiLoadEventEmitter(
        uiLoadEventListener = uiLoadEventListener,
        clock = clock,
    )
    return if (versionChecker.isAtLeast(VERSION_CODES.Q)) {
        ActivityLoadEventEmitter(uiLoadEventEmitter)
    } else {
        LegacyActivityLoadEventEmitter(uiLoadEventEmitter)
    }
}

fun Activity.observeOpening() = javaClass.isAnnotationPresent(ObservedActivity::class.java)

/**
 * Implementation that works with Android 10+ APIs
 */
@RequiresApi(VERSION_CODES.Q)
private class ActivityLoadEventEmitter(
    private val uiLoadEventEmitter: UiLoadEventEmitter
) : ActivityLifecycleListener {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.create(activity)
        }
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.createEnd(activity)
        }
    }

    override fun onActivityPreStarted(activity: Activity) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.start(activity)
        }
    }

    override fun onActivityPostStarted(activity: Activity) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.startEnd(activity)
        }
    }

    override fun onActivityPreResumed(activity: Activity) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.resume(activity)
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.resumeEnd(activity)
        }
    }

    override fun onActivityPrePaused(activity: Activity) {
        uiLoadEventEmitter.abandonTrace(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        uiLoadEventEmitter.reset(activity)
    }
}

/**
 * Version of [ActivityLoadEventEmitter] that works with all Android version and used for Android 9 or lower
 */
private class LegacyActivityLoadEventEmitter(
    private val uiLoadEventEmitter: UiLoadEventEmitter
) : ActivityLifecycleListener {

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.create(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.createEnd(activity)
            uiLoadEventEmitter.start(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.observeOpening()) {
            uiLoadEventEmitter.startEnd(activity)
            uiLoadEventEmitter.resume(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        uiLoadEventEmitter.abandonTrace(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        uiLoadEventEmitter.reset(activity)
    }
}

/**
 * Maps an Activity instance's UI Load events the app-wide UI [UiLoadEventListener]
 */
private class UiLoadEventEmitter(
    private val uiLoadEventListener: UiLoadEventListener,
    private val clock: Clock,
) {
    fun abandonTrace(activity: Activity) {
        uiLoadEventListener.abandon(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    fun reset(activity: Activity) {
        uiLoadEventListener.reset(
            lastInstanceId = traceInstanceId(activity),
        )
    }

    fun create(activity: Activity) {
        uiLoadEventListener.create(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs(),
            manualEnd = false,
        )
    }

    fun createEnd(activity: Activity) {
        uiLoadEventListener.createEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    fun start(activity: Activity) {
        uiLoadEventListener.start(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs(),
            manualEnd = false,
        )
    }

    fun startEnd(activity: Activity) {
        uiLoadEventListener.startEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    fun resume(activity: Activity) {
        uiLoadEventListener.resume(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    fun resumeEnd(activity: Activity) {
        uiLoadEventListener.resumeEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun traceInstanceId(activity: Activity): Int = activity.hashCode()

    private fun nowMs(): Long = clock.now().nanosToMillis()
}
