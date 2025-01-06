package io.embrace.android.embracesdk.internal.ui

import android.app.Activity

/**
 * Interface that allows callbacks to be registered and invoked when UI draw events happen
 */
interface DrawEventEmitter {
    fun registerFirstDrawCallback(activity: Activity, completionCallback: () -> Unit)

    fun unregisterFirstDrawCallback(activity: Activity)
}
