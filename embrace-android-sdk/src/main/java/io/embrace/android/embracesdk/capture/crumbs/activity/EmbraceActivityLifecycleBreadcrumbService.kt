package io.embrace.android.embracesdk.capture.crumbs.activity

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.clock.Clock
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.ActivityLifecycleBreadcrumb
import io.embrace.android.embracesdk.payload.ActivityLifecycleData
import io.embrace.android.embracesdk.payload.ActivityLifecycleState
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

// TODO future: decide on a configurable limit?
private const val LIMIT = 80

/**
 * Captures activity lifecycle breadcrumbs whenever the system alters the lifecycle of any
 * Activity in the app.
 *
 * Breadcrumbs are captured separately for each activity and the duration of each activity
 * lifecycle is also collected. This allows in principle for aggregate metrics on
 * abnormally long lifecycle to be detected and shown to the user.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class EmbraceActivityLifecycleBreadcrumbService(
    private val configService: ConfigService,
    private val clock: Clock
) : Application.ActivityLifecycleCallbacks, ActivityLifecycleBreadcrumbService {

    // store breadcrumbs in a map with the activity hash code as the key
    private val crumbs = ConcurrentHashMap<String, Queue<ActivityLifecycleBreadcrumb>>()

    // onCreate()
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) =
        createBreadcrumb(activity, ActivityLifecycleState.ON_CREATE, savedInstanceState != null)

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) =
        endBreadcrumb(activity)

    // onStart()
    override fun onActivityPreStarted(activity: Activity) = createBreadcrumb(
        activity,
        ActivityLifecycleState.ON_START
    )

    override fun onActivityPostStarted(activity: Activity) = endBreadcrumb(activity)

    // onResume()
    override fun onActivityPreResumed(activity: Activity) = createBreadcrumb(
        activity,
        ActivityLifecycleState.ON_RESUME
    )

    override fun onActivityPostResumed(activity: Activity) = endBreadcrumb(activity)

    // onPause()
    override fun onActivityPrePaused(activity: Activity) = createBreadcrumb(
        activity,
        ActivityLifecycleState.ON_PAUSE
    )

    override fun onActivityPostPaused(activity: Activity) = endBreadcrumb(activity)

    // onStop()
    override fun onActivityPreStopped(activity: Activity) = createBreadcrumb(
        activity,
        ActivityLifecycleState.ON_STOP
    )

    override fun onActivityPostStopped(activity: Activity) = endBreadcrumb(activity)

    // onDestroy()
    override fun onActivityPreDestroyed(activity: Activity) = createBreadcrumb(
        activity,
        ActivityLifecycleState.ON_DESTROY
    )

    override fun onActivityPostDestroyed(activity: Activity) = endBreadcrumb(activity)

    // onSaveInstanceState()
    override fun onActivityPreSaveInstanceState(activity: Activity, outState: Bundle) =
        createBreadcrumb(activity, ActivityLifecycleState.ON_SAVE_INSTANCE_STATE)

    override fun onActivityPostSaveInstanceState(activity: Activity, outState: Bundle) =
        endBreadcrumb(activity)

    // no-ops
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    /**
     * Creates a breadcrumb for the upcoming state change in the Activity lifecycle.
     */
    private fun createBreadcrumb(
        activity: Activity,
        state: ActivityLifecycleState,
        bundlePresent: Boolean? = false
    ) {
        val name = activity.javaClass.simpleName
        val queue = crumbs.getOrPut(name) { ConcurrentLinkedQueue() }
        val crumb = ActivityLifecycleBreadcrumb(
            name,
            state,
            clock.now(),
            bundlePresent
        )
        queue.add(crumb)

        while (queue.size > LIMIT) {
            queue.poll()
        }
    }

    private fun endBreadcrumb(activity: Activity) {
        val name = activity.javaClass.simpleName
        val queue = crumbs[name]
        val crumb = queue?.lastOrNull() ?: return
        crumb.end = clock.now()
    }

    override fun cleanCollections() {
        crumbs.clear()
    }

    override fun getCapturedData(): List<ActivityLifecycleData> = when {
        configService.sdkModeBehavior.isBetaFeaturesEnabled() -> transformToSessionData(crumbs.values)
        else -> emptyList()
    }

    private fun transformToSessionData(data: Collection<Queue<ActivityLifecycleBreadcrumb>>) = data
        .filter { it.isNotEmpty() }
        .map { entry ->
            val copy = entry.toList()
            val name = copy.firstOrNull()?.activity
            ActivityLifecycleData(name, copy)
        }
}
