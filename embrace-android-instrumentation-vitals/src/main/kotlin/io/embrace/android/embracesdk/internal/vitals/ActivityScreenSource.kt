package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler
import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * Starts a screen for each Activity when it resumes, and stops it when it pauses.
 */
internal class ActivityScreenSource(
    private val clock: Clock,
    private val eventBus: EventBus,
    private val errorHandler: ErrorHandler,
    versionChecker: VersionChecker,
) : AutoCloseable {

    private val resumedKey: EventKey<Activity>
    private val pausedKey: EventKey<Activity>

    init {
        val usePrePostCallbacks = versionChecker.isAtLeast(Build.VERSION_CODES.Q)
        resumedKey = if (usePrePostCallbacks) ActivityLifecycle.PRE_RESUMED else ActivityLifecycle.RESUMED
        pausedKey = if (usePrePostCallbacks) ActivityLifecycle.POST_PAUSED else ActivityLifecycle.PAUSED
    }

    private val onResumed = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            val id = activity.getId()
            val startMs = clock.now()
            eventBus.emit(ScreenStart.KEY, ScreenStart(activity.localClassName, ScreenSource.ACTIVITY, id, startMs))
        }
    }

    private val onPaused = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            eventBus.emit(
                ScreenStop.KEY,
                ScreenStop(ScreenSource.ACTIVITY, activity.getId(), clock.now(), ScreenStop.Reason.HIDDEN),
            )
        }
    }

    fun subscribe() {
        eventBus.addHandler(resumedKey, onResumed)
        eventBus.addHandler(pausedKey, onPaused)
    }

    override fun close() {
        eventBus.removeHandler(resumedKey, onResumed)
        eventBus.removeHandler(pausedKey, onPaused)
    }
}
