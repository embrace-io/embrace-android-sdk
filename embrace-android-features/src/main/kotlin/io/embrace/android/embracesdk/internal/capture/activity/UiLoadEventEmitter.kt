package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.annotation.ObservedActivity
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.opentelemetry.sdk.common.Clock

/**
 * Maps [ActivityLifecycleCallbacks] events to [UiLoadEvents] depending on the current state of the app and capabilities of the OS.
 *
 * The purpose of this is to leverage Activity lifecycle events to provide data for the underlying workflow to bring a new Activity on
 * screen. Due to the varying capabilities of the APIs available on the different versions of Android, the precise triggering events for
 * the start and intermediate steps may differ.
 *
 * See [UiLoadTraceEmitter] for details.
 */
class UiLoadEventEmitter(
    private val uiLoadEvents: UiLoadEvents,
    private val clock: Clock,
    private val versionChecker: VersionChecker,
) : ActivityLifecycleListener {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeOpening()) {
            create(activity)
        }
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (activity.observeOpening() && !versionChecker.firePrePostEvents()) {
            create(activity)
        }
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeOpening()) {
            createEnd(activity)
        }
    }

    override fun onActivityPreStarted(activity: Activity) {
        if (activity.observeOpening()) {
            start(activity)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.observeOpening() && !versionChecker.firePrePostEvents()) {
            createEnd(activity)
            start(activity)
        }
    }

    override fun onActivityPostStarted(activity: Activity) {
        if (activity.observeOpening()) {
            startEnd(activity)
        }
    }

    override fun onActivityPreResumed(activity: Activity) {
        if (activity.observeOpening()) {
            resume(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.observeOpening() && !versionChecker.firePrePostEvents()) {
            startEnd(activity)
            resume(activity)
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        if (activity.observeOpening()) {
            resumeEnd(activity)
        }
    }

    override fun onActivityPrePaused(activity: Activity) {
        abandonTrace(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (!versionChecker.firePrePostEvents()) {
            abandonTrace(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        reset(activity)
    }

    private fun abandonTrace(activity: Activity) {
        uiLoadEvents.abandon(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun reset(activity: Activity) {
        uiLoadEvents.reset(
            instanceId = traceInstanceId(activity),
        )
    }

    private fun create(activity: Activity) {
        uiLoadEvents.create(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun createEnd(activity: Activity) {
        uiLoadEvents.createEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun start(activity: Activity) {
        uiLoadEvents.start(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun startEnd(activity: Activity) {
        uiLoadEvents.startEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun resume(activity: Activity) {
        uiLoadEvents.resume(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun resumeEnd(activity: Activity) {
        uiLoadEvents.resumeEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun VersionChecker.firePrePostEvents(): Boolean = isAtLeast(Build.VERSION_CODES.Q)

    private fun traceInstanceId(activity: Activity): Int = activity.hashCode()

    private fun nowMs(): Long = clock.now().nanosToMillis()

    private fun Activity.observeOpening() = javaClass.isAnnotationPresent(ObservedActivity::class.java)
}
