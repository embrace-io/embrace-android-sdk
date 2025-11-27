package io.embrace.android.embracesdk.internal.instrumentation.startup.ui

import android.app.Activity
import android.os.Build
import android.os.Build.VERSION_CODES
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.utils.VersionChecker

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
        drawCompleteCallback: () -> Unit
    )

    /**
     * Unregister any first draw callbacks registered for the given Activity instance
     */
    fun unregisterFirstDrawCallback(activity: Activity)
}

internal fun createDrawEventEmitter(
    versionChecker: VersionChecker,
    logger: EmbLogger,
): DrawEventEmitter? = if (supportFrameCommitCallback(versionChecker)) {
    FirstDrawDetector(logger)
} else if (hasRenderEvent(versionChecker)) {
    HandlerMessageDrawDetector(AndroidMainThreadHandler())
} else {
    null
}

internal fun hasRenderEvent(versionChecker: VersionChecker) = versionChecker.isAtLeast(VERSION_CODES.M)

internal fun supportFrameCommitCallback(versionChecker: VersionChecker) = versionChecker.isAtLeast(VERSION_CODES.Q) &&
    (Build.VERSION.SDK_INT != VERSION_CODES.S && Build.VERSION.SDK_INT != VERSION_CODES.S_V2)
