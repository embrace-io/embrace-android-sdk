package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Relays every activity lifecycle callback [application] dispatches onto [eventBus], on the [ActivityLifecycle] key named after it.
 */
class ActivityLifecycleRelay(
    private val application: Application,
    private val eventBus: EventBus,
    private val errorHandler: ErrorHandler,
) : Application.ActivityLifecycleCallbacks, AutoCloseable {

    /**
     * Starts relaying the callbacks [application] dispatches.
     */
    fun register() {
        errorHandler.guardSdkCode(SdkErrorType.RegistrationFail) {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    override fun close() {
        runCatching {
            application.unregisterActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) =
        eventBus.emit(ActivityLifecycle.PRE_CREATED, activity)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
        eventBus.emit(ActivityLifecycle.CREATED, ActivityCreated(activity, savedInstanceState))

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) =
        eventBus.emit(ActivityLifecycle.POST_CREATED, activity)

    override fun onActivityPreStarted(activity: Activity) = eventBus.emit(ActivityLifecycle.PRE_STARTED, activity)

    override fun onActivityStarted(activity: Activity) = eventBus.emit(ActivityLifecycle.STARTED, activity)

    override fun onActivityPostStarted(activity: Activity) = eventBus.emit(ActivityLifecycle.POST_STARTED, activity)

    override fun onActivityPreResumed(activity: Activity) = eventBus.emit(ActivityLifecycle.PRE_RESUMED, activity)

    override fun onActivityResumed(activity: Activity) = eventBus.emit(ActivityLifecycle.RESUMED, activity)

    override fun onActivityPostResumed(activity: Activity) = eventBus.emit(ActivityLifecycle.POST_RESUMED, activity)

    override fun onActivityPrePaused(activity: Activity) = eventBus.emit(ActivityLifecycle.PRE_PAUSED, activity)

    override fun onActivityPaused(activity: Activity) = eventBus.emit(ActivityLifecycle.PAUSED, activity)

    override fun onActivityPostPaused(activity: Activity) = eventBus.emit(ActivityLifecycle.POST_PAUSED, activity)

    override fun onActivityPreStopped(activity: Activity) = eventBus.emit(ActivityLifecycle.PRE_STOPPED, activity)

    override fun onActivityStopped(activity: Activity) = eventBus.emit(ActivityLifecycle.STOPPED, activity)

    override fun onActivityPostStopped(activity: Activity) = eventBus.emit(ActivityLifecycle.POST_STOPPED, activity)

    override fun onActivityPreSaveInstanceState(activity: Activity, outState: Bundle) =
        eventBus.emit(ActivityLifecycle.PRE_SAVE_INSTANCE_STATE, activity)

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
        eventBus.emit(ActivityLifecycle.SAVE_INSTANCE_STATE, activity)

    override fun onActivityPostSaveInstanceState(activity: Activity, outState: Bundle) =
        eventBus.emit(ActivityLifecycle.POST_SAVE_INSTANCE_STATE, activity)

    override fun onActivityPreDestroyed(activity: Activity) = eventBus.emit(ActivityLifecycle.PRE_DESTROYED, activity)

    override fun onActivityDestroyed(activity: Activity) = eventBus.emit(ActivityLifecycle.DESTROYED, activity)

    override fun onActivityPostDestroyed(activity: Activity) = eventBus.emit(ActivityLifecycle.POST_DESTROYED, activity)
}
