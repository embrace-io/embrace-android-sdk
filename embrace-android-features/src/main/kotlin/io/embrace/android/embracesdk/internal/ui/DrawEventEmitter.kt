package io.embrace.android.embracesdk.internal.ui

import android.app.Activity

/**
 * Interface that allows callbacks to be registered and invoked when UI draw events happen
 */
interface DrawEventEmitter {
    /**
     * Register the given callback to the UI inside of the given activity instance to be invoked when the first frame
     * has drawn.
     */
    fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        firstFrameDeliveredCallback: () -> Unit
    )

    /**
     * Unregister any first draw callbacks registered for the given Activity instance
     */
    fun unregisterFirstDrawCallback(activity: Activity)
}
