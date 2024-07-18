package io.embrace.android.embracesdk.internal.capture.activity

import android.app.Activity
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.opentelemetry.sdk.common.Clock

internal class LoadEventEmitter(
    private val openEvents: LoadEvents,
    private val clock: Clock,
    private val versionChecker: VersionChecker,
): ActivityLifecycleListener {
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        create(activity)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        if (!versionChecker.hasPrePostEvents()) {
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
        if (!versionChecker.hasPrePostEvents()) {
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
        if (!versionChecker.hasPrePostEvents()) {
            startEnd(activity)
            resumeEnd(activity)
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        resumeEnd(activity)
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
            timestampMs = nowMs()
        )
    }

    private fun resumeEnd(activity: Activity) {
        openEvents.resumeEnd(
            instanceId = traceInstanceId(activity),
            timestampMs = nowMs()
        )
    }

    private fun VersionChecker.hasPrePostEvents(): Boolean = isAtLeast(Build.VERSION_CODES.Q)

    private fun traceInstanceId(activity: Activity): Int = activity.hashCode()

    private fun nowMs(): Long = clock.now().nanosToMillis()
}