package io.embrace.android.embracesdk.internal.api

import android.app.Activity
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * API to control and customize Embrace instrumentation
 */
@InternalApi
public interface InstrumentationApi {

    /**
     * Notify the Embrace UI Load instrumentation that the given [Activity] instance has fully loaded, so its associated
     * trace can be stopped
     */
    public fun activityLoaded(activity: Activity)
}
