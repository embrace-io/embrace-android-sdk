package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.utils.event.EventBus

internal fun EventBus.startActivity(activity: Activity) {
    emit(ActivityLifecycle.PRE_STARTED, activity)
    emit(ActivityLifecycle.STARTED, activity)
    emit(ActivityLifecycle.POST_STARTED, activity)
}

internal fun EventBus.resumeActivity(activity: Activity) {
    emit(ActivityLifecycle.PRE_RESUMED, activity)
    emit(ActivityLifecycle.RESUMED, activity)
    emit(ActivityLifecycle.POST_RESUMED, activity)
}

internal fun EventBus.pauseActivity(activity: Activity) {
    emit(ActivityLifecycle.PRE_PAUSED, activity)
    emit(ActivityLifecycle.PAUSED, activity)
    emit(ActivityLifecycle.POST_PAUSED, activity)
}

internal fun EventBus.destroyActivity(activity: Activity) {
    emit(ActivityLifecycle.PRE_DESTROYED, activity)
    emit(ActivityLifecycle.DESTROYED, activity)
    emit(ActivityLifecycle.POST_DESTROYED, activity)
}
