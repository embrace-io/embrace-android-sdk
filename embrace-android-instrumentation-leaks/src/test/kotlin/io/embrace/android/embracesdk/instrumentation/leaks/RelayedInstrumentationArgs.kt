package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import org.robolectric.RuntimeEnvironment

/**
 * Args on the Robolectric application, whose activity callbacks are relayed onto the args' event bus as the SDK's relay does.
 */
internal fun relayedInstrumentationArgs(): FakeInstrumentationArgs =
    FakeInstrumentationArgs(RuntimeEnvironment.getApplication()).also { args ->
        args.application.registerActivityLifecycleCallbacks(BusRelay(args.eventBus))
    }

/**
 * Relays the callbacks [ActivityLeakDetectionLifecycleCallbacks] handles onto [eventBus].
 */
private class BusRelay(private val eventBus: EventBus) : Application.ActivityLifecycleCallbacks {
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) =
        eventBus.emit(ActivityLifecycle.PRE_CREATED, activity)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
        eventBus.emit(ActivityLifecycle.CREATED, ActivityCreated(activity, savedInstanceState))

    override fun onActivityResumed(activity: Activity) = eventBus.emit(ActivityLifecycle.RESUMED, activity)

    override fun onActivityDestroyed(activity: Activity) = eventBus.emit(ActivityLifecycle.DESTROYED, activity)

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
