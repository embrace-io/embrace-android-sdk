package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityDestroyed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityPaused
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityResumed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityStarted
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.Backgrounded
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler
import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * Tracks Activities coming into and out of view through the [ActivityLifecycle] events, but listens to [ProcessStateListener]
 * when it comes to tracking app backgrounding in order to synchronize with the rest of the SDK's app backgrounding logic.
 *
 * The time that this component's listeners fire is the canonical time for the signal, whenever it is processed downstream.
 */
internal class ActivityNavigationTracker(
    private val clock: Clock,
    private val eventBus: EventBus,
    private val navigationTrackingService: NavigationTrackingService,
    private val errorHandler: ErrorHandler,
    versionChecker: VersionChecker = BuildVersionChecker,
) : ProcessStateListener, AutoCloseable {

    private val startedKey: EventKey<Activity>
    private val resumedKey: EventKey<Activity>
    private val pausedKey: EventKey<Activity>

    init {
        val usePrePostCallbacks = versionChecker.isAtLeast(Build.VERSION_CODES.Q)
        startedKey = if (usePrePostCallbacks) ActivityLifecycle.PRE_STARTED else ActivityLifecycle.STARTED
        resumedKey = if (usePrePostCallbacks) ActivityLifecycle.PRE_RESUMED else ActivityLifecycle.RESUMED
        pausedKey = if (usePrePostCallbacks) ActivityLifecycle.POST_PAUSED else ActivityLifecycle.PAUSED
    }

    private val onStarted = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            eventBus.emit(NavigationSignal.KEY, ActivityStarted(activity.getId(), clock.now()))
        }
    }

    private val onResumed = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            eventBus.emit(NavigationSignal.KEY, ActivityResumed(activity.getId(), activity.localClassName, clock.now()))

            // Add screen source tracking after the resume signal is emitted to mimic how the rememberNavController Composable will do it.
            navigationTrackingService.trackNavigation(activity)
        }
    }

    private val onPaused = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            eventBus.emit(NavigationSignal.KEY, ActivityPaused(activity.getId(), clock.now()))
        }
    }

    private val onDestroyed = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            eventBus.emit(NavigationSignal.KEY, ActivityDestroyed(activity.getId(), clock.now()))
        }
    }

    /**
     * Starts tracking Activities, from the pre and post events on Q and above and from the main events below it.
     */
    fun subscribe() {
        eventBus.addHandler(startedKey, onStarted)
        eventBus.addHandler(resumedKey, onResumed)
        eventBus.addHandler(pausedKey, onPaused)
        eventBus.addHandler(ActivityLifecycle.DESTROYED, onDestroyed)
    }

    override fun close() {
        eventBus.removeHandler(startedKey, onStarted)
        eventBus.removeHandler(resumedKey, onResumed)
        eventBus.removeHandler(pausedKey, onPaused)
        eventBus.removeHandler(ActivityLifecycle.DESTROYED, onDestroyed)
    }

    override fun onBackground() {
        eventBus.emit(NavigationSignal.KEY, Backgrounded(clock.now()))
    }

    override fun onForeground() {}
}
