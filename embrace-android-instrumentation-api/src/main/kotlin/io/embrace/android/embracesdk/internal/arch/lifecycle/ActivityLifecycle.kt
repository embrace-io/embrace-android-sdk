package io.embrace.android.embracesdk.internal.arch.lifecycle

import android.app.Activity
import android.os.Bundle
import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The keys each `Application.ActivityLifecycleCallbacks` callback is relayed on, one per callback and named after it.
 */
object ActivityLifecycle {
    @JvmField val PRE_CREATED = EventKey<Activity>()

    @JvmField val CREATED = EventKey<ActivityCreated>()

    @JvmField val POST_CREATED = EventKey<Activity>()

    @JvmField val PRE_STARTED = EventKey<Activity>()

    @JvmField val STARTED = EventKey<Activity>()

    @JvmField val POST_STARTED = EventKey<Activity>()

    @JvmField val PRE_RESUMED = EventKey<Activity>()

    @JvmField val RESUMED = EventKey<Activity>()

    @JvmField val POST_RESUMED = EventKey<Activity>()

    @JvmField val PRE_PAUSED = EventKey<Activity>()

    @JvmField val PAUSED = EventKey<Activity>()

    @JvmField val POST_PAUSED = EventKey<Activity>()

    @JvmField val PRE_STOPPED = EventKey<Activity>()

    @JvmField val STOPPED = EventKey<Activity>()

    @JvmField val POST_STOPPED = EventKey<Activity>()

    @JvmField val PRE_SAVE_INSTANCE_STATE = EventKey<Activity>()

    @JvmField val SAVE_INSTANCE_STATE = EventKey<Activity>()

    @JvmField val POST_SAVE_INSTANCE_STATE = EventKey<Activity>()

    @JvmField val PRE_DESTROYED = EventKey<Activity>()

    @JvmField val DESTROYED = EventKey<Activity>()

    @JvmField val POST_DESTROYED = EventKey<Activity>()
}

/**
 * An [Activity] being created, with the state it is being recreated from, or null when it is created for the first time.
 */
class ActivityCreated(
    @JvmField val activity: Activity,
    @JvmField val savedInstanceState: Bundle?,
)
