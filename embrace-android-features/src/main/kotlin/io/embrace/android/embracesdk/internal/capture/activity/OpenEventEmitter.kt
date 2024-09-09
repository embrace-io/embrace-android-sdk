package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.opentelemetry.sdk.common.Clock

/**
 * Maps [ActivityLifecycleCallbacks] events to [OpenEvents] depending on the current state of the app and capabilities of the OS.
 *
 * The purpose of this is to leverage Activity lifecycle events to provide data for the underlying workflow to bring a new Activity on
 * screen. Due to the varying capabilities of the APIs available on the different versions of Android, the precise triggering events for
 * the start and intermediate steps may differ.
 *
 * See [OpenTraceEmitter] for details.
 */
class OpenEventEmitter(
    private val openEvents: OpenEvents,
    private val clock: Clock,
    private val versionChecker: VersionChecker,
) : ActivityLifecycleListener {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        create(activity)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (!versionChecker.firePrePostEvents()) {
            create(activity)
        }
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        createEnd(activity)
    }

    override fun onActivityPreStarted(activity: Activity) {
        start(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (!versionChecker.firePrePostEvents()) {
            createEnd(activity)
            start(activity)
        }
    }

    override fun onActivityPostStarted(activity: Activity) {
        startEnd(activity)
    }

    override fun onActivityPreResumed(activity: Activity) {
        resume(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        if (!versionChecker.firePrePostEvents()) {
            startEnd(activity)
            resume(activity)
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        resumeEnd(activity)
    }

    override fun onActivityPrePaused(activity: Activity) {
        resetTrace(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (!versionChecker.firePrePostEvents()) {
            resetTrace(activity)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        hibernate(activity)
    }

    private fun resetTrace(activity: Activity) {
        openEvents.resetTrace(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun hibernate(activity: Activity) {
        openEvents.hibernate(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun create(activity: Activity) {
        openEvents.create(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun createEnd(activity: Activity) {
        openEvents.createEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun start(activity: Activity) {
        openEvents.start(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun startEnd(activity: Activity) {
        openEvents.startEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun resume(activity: Activity) {
        openEvents.resume(
            instanceId = traceInstanceId(activity),
            activityName = activity.localClassName,
            timestampMs = nowMs()
        )
    }

    private fun resumeEnd(activity: Activity) {
        openEvents.resumeEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun VersionChecker.firePrePostEvents(): Boolean = isAtLeast(Build.VERSION_CODES.Q)

    private fun traceInstanceId(activity: Activity): Int = activity.hashCode()

    private fun nowMs(): Long = clock.now().nanosToMillis()
}
