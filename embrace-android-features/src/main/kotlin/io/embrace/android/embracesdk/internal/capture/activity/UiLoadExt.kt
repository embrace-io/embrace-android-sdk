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
    val lifecycleEventEmitter = LifecycleEventEmitter(
        uiLoadEventListener = uiLoadEventListener,
        clock = clock,
    )
    return if (versionChecker.isAtLeast(VERSION_CODES.Q)) {
        ActivityLoadEventEmitter(lifecycleEventEmitter)
    } else {
        LegacyActivityLoadEventEmitter(lifecycleEventEmitter)
    }
}

fun Activity.observeOpening() = javaClass.isAnnotationPresent(ObservedActivity::class.java)

/**
 * Implementation that works with Android 10+ APIs
 */
@RequiresApi(VERSION_CODES.Q)
private class ActivityLoadEventEmitter(
    private val lifecycleEventEmitter: LifecycleEventEmitter
) : ActivityLifecycleListener {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.create(activity)
        }
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.createEnd(activity)
        }
    }

    override fun onActivityPreStarted(activity: Activity) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.start(activity)
        }
    }

    override fun onActivityPostStarted(activity: Activity) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.startEnd(activity)
        }
    }

    override fun onActivityPreResumed(activity: Activity) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.resume(activity)
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.resumeEnd(activity)
        }
    }

    override fun onActivityPrePaused(activity: Activity) {
        lifecycleEventEmitter.pause(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        lifecycleEventEmitter.stop(activity)
    }
}

/**
 * Version of [ActivityLoadEventEmitter] that works with all Android version and used for Android 9 or lower
 */
private class LegacyActivityLoadEventEmitter(
    private val lifecycleEventEmitter: LifecycleEventEmitter
) : ActivityLifecycleListener {

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.create(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.createEnd(activity)
            lifecycleEventEmitter.start(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.observeOpening()) {
            lifecycleEventEmitter.startEnd(activity)
            lifecycleEventEmitter.resume(activity)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        lifecycleEventEmitter.pause(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        lifecycleEventEmitter.stop(activity)
    }
}

/**
 * Maps lifecycle events to UI Load events implemented by the given [UiLoadEventListener]
 */
private class LifecycleEventEmitter(
    private val uiLoadEventListener: UiLoadEventListener,
    private val clock: Clock,
) {

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

    fun pause(activity: Activity) {
        uiLoadEventListener.exit(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    fun stop(activity: Activity) {
        uiLoadEventListener.reset(
            lastInstanceId = traceInstanceId(activity),
        )
    }

    private fun traceInstanceId(activity: Activity): Int = activity.hashCode()

    private fun nowMs(): Long = clock.now().nanosToMillis()
}
