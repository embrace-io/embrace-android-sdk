package io.embrace.android.embracesdk.testframework.actions

import android.app.Activity
import androidx.lifecycle.Lifecycle
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/**
 * Interface for performing actions on the [Embrace] instance under test
 */
internal class EmbraceActionInterface(
    private val setup: EmbraceSetupInterface,
    private val bootstrapper: ModuleInitBootstrapper,
    private val embraceSupplier: () -> SdkApi,
) {

    /**
     * The [Embrace] instance that can be used for testing
     */
    val embrace: SdkApi by lazy { embraceSupplier() }

    val clock: FakeClock
        get() = setup.getClock()

    private var appHasStarted: Boolean = false

    /**
     * Starts & ends a session for the purposes of testing. An action can be supplied as a lambda
     * parameter: any code inside the lambda will be executed, so can be used to add breadcrumbs,
     * send log messages etc, while the session is active. The end session message is returned so
     * that the caller can perform further assertions if needed.
     *
     * This function fakes the lifecycle events that trigger a session start & end. The session
     * should always be 30s long. Additionally, it performs assertions against fields that
     * are guaranteed not to change in the start/end message.
     */
    internal fun recordSession(
        action: EmbraceActionInterface.() -> Unit = {},
    ): SessionTimestamps {
        val sessionAction: () -> Unit = {
            // perform a custom action during the session boundary, e.g. adding a breadcrumb.
            this.action()
            // end session 30s later by entering background
            setup.getClock().tick(30000)
        }
        val activityAndAction = listOf(Robolectric.buildActivity(Activity::class.java) to sessionAction)

        return if (!appHasStarted) {
            appHasStarted = true
            simulateOpeningActivities(
                addStartupActivity = false,
                startInBackground = true,
                activitiesAndActions = activityAndAction
            ).let { executionTimestamps ->
                val isBackgroundActivityEnabled =
                    bootstrapper.configModule.configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()

                SessionTimestamps(
                    startTimeMs = if (isBackgroundActivityEnabled) {
                        executionTimestamps.firstForegroundTimeMs
                    } else {
                        executionTimestamps.executionStartTimeMs
                    },
                    foregroundTimeMs = executionTimestamps.firstForegroundTimeMs,
                    actionTimeMs = executionTimestamps.firstActionTimeMs,
                    endTimeMs = executionTimestamps.lastBackgroundTimeMs
                )
            }
        } else {
            simulateOpeningActivities(
                addStartupActivity = false,
                startInBackground = true,
                activitiesAndActions = activityAndAction
            ).let { executionTimestamps ->
                SessionTimestamps(
                    startTimeMs = executionTimestamps.firstForegroundTimeMs,
                    foregroundTimeMs = executionTimestamps.firstForegroundTimeMs,
                    actionTimeMs = executionTimestamps.firstActionTimeMs,
                    endTimeMs = executionTimestamps.lastBackgroundTimeMs
                )
            }
        }
    }

    private fun onForeground() {
        setup.fakeLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun onBackground() {
        setup.fakeLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun simulateNetworkChange(status: NetworkStatus) {
        setup.fakeNetworkConnectivityService.networkStatus = status
    }

    internal fun simulateOpeningActivities(
        addStartupActivity: Boolean = true,
        startInBackground: Boolean = false,
        endInBackground: Boolean = true,
        createFirstActivity: Boolean = true,
        invokeManualEnd: Boolean = false,
        activitiesAndActions: List<Pair<ActivityController<*>, () -> Unit>> = listOf(
            Robolectric.buildActivity(Activity::class.java) to {},
        ),
    ): AppExecutionTimestamps {
        val appExecutionTimes = AppExecutionTimestamps(executionStartTimeMs = clock.now())
        var lastActivity: ActivityController<*>? = if (addStartupActivity) {
            Robolectric.buildActivity(Activity::class.java)
        } else {
            null
        }?.apply {
            create()
            start()
            appExecutionTimes.firstForegroundTimeMs = clock.now()
            onForeground()
            resume()
            pause()
            if (startInBackground) {
                stop()
                appExecutionTimes.lastBackgroundTimeMs = clock.now()
                onBackground()
                setup.getClock().tick(STARTUP_BACKGROUND_TIME)
            } else {
                setup.getClock().tick(ACTIVITY_GAP)
            }
        }
        activitiesAndActions.forEachIndexed { index, (activityController, action) ->
            if (index != 0 || createFirstActivity) {
                activityController.create()
                setup.getClock().tick(LIFECYCLE_EVENT_GAP)
            }
            if (index == 0 && startInBackground) {
                if (appExecutionTimes.firstForegroundTimeMs == 0L) {
                    appExecutionTimes.firstForegroundTimeMs = clock.now()
                }
                onForeground()
            }
            activityController.start()
            setup.getClock().tick(LIFECYCLE_EVENT_GAP)
            activityController.resume()

            setup.getClock().tick(LIFECYCLE_EVENT_GAP)

            if (invokeManualEnd) {
                embrace.addLoadTraceAttribute(activityController.get(), "manual-end", "true")
                val startTime = clock.now()
                setup.getClock().tick(LIFECYCLE_EVENT_GAP)
                val endTime = clock.now()
                embrace.addLoadTraceChildSpan(
                    activity = activityController.get(),
                    name = "loading-time",
                    startTimeMs = startTime,
                    endTimeMs = endTime
                )
                embrace.activityLoaded(activityController.get())
            }
            lastActivity?.stop()

            appExecutionTimes.firstActionTimeMs = clock.now()
            action()

            setup.getClock().tick(POST_ACTIVITY_ACTION_DWELL)
            activityController.pause()
            setup.getClock().tick(ACTIVITY_GAP)
            lastActivity = activityController
        }

        if (endInBackground) {
            setup.getClock().tick()
            appExecutionTimes.lastBackgroundTimeMs = clock.now()
            lastActivity?.stop()
            onBackground()
        }

        return appExecutionTimes
    }

    fun simulateJvmUncaughtException(exc: Throwable) {
        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), exc)
    }

    fun alterPowerSaveMode(powerSaveMode: Boolean) {
        val dataSource = checkNotNull(bootstrapper.featureModule.lowPowerDataSource.dataSource)
        dataSource.onPowerSaveModeChanged(powerSaveMode)
    }

    fun alterConnectivityStatus(networkStatus: NetworkStatus) {
        val dataSource = checkNotNull(bootstrapper.featureModule.networkStatusDataSource.dataSource)
        dataSource.onNetworkConnectivityStatusChanged(networkStatus)
    }

    fun alterThermalState(thermalState: Int) {
        val dataSource = checkNotNull(bootstrapper.featureModule.thermalStateDataSource.dataSource)
        dataSource.handleThermalStateChange(thermalState)
    }

    companion object {
        const val LIFECYCLE_EVENT_GAP: Long = 10L
        const val ACTIVITY_GAP: Long = 100L
        const val STARTUP_BACKGROUND_TIME: Long = 1000L
        const val POST_ACTIVITY_ACTION_DWELL: Long = 10000L
    }
}
