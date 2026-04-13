package io.embrace.android.embracesdk.testframework.actions

import android.app.Activity
import androidx.lifecycle.Lifecycle
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.NavControllerFragmentActivity
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.capture.connectivity.toOptimisticStatus
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
        isBackgroundActivityEnabled: Boolean = true,
        activityClass: Class<out Activity> = Activity::class.java,
        action: EmbraceActionInterface.() -> Unit = {},
    ): SessionPartTimestamps {
        val sessionAction: () -> Unit = {
            // perform a custom action during the session boundary, e.g. adding a breadcrumb.
            this.action()
            // end session 30s later by entering background
            setup.getClock().tick(30000)
        }
        val activityAndAction = listOf(Robolectric.buildActivity(activityClass) to sessionAction)

        return if (!appHasStarted) {
            appHasStarted = true
            simulateOpeningActivities(
                addStartupActivity = false,
                startInBackground = true,
                activitiesAndActions = activityAndAction
            ).let { executionTimestamps ->
                SessionPartTimestamps(
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
                SessionPartTimestamps(
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

    fun simulateConnectivityChange(status: ConnectivityStatus) {
        setup.fakeNetworkConnectivityService.connectivityStatus = status
    }

    fun simulateConnectionTypeChange(connectionType: ConnectionType, legacyBehavior: Boolean = false) {
        if (legacyBehavior) {
            simulateConnectivityChange(connectionType.toOptimisticStatus())
        } else {
            when (connectionType) {
                ConnectionType.WIFI -> {
                    simulateConnectivityChange(ConnectivityStatus.Wifi(false))
                    clock.tick(CONNECTIVITY_VALIDATION_GAP)
                    simulateConnectivityChange(ConnectivityStatus.Wifi(true))
                }

                ConnectionType.WAN -> {
                    simulateConnectivityChange(ConnectivityStatus.Wan(false))
                    clock.tick(CONNECTIVITY_VALIDATION_GAP)
                    simulateConnectivityChange(ConnectivityStatus.Wan(true))
                }

                ConnectionType.UNKNOWN -> {
                    simulateConnectivityChange(ConnectivityStatus.Unknown(false))
                    clock.tick(CONNECTIVITY_VALIDATION_GAP)
                    simulateConnectivityChange(ConnectivityStatus.Unknown(true))
                }

                ConnectionType.NONE -> {
                    simulateConnectivityChange(ConnectivityStatus.None)
                }
            }
        }
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

    /**
     * Simulates opening a [NavControllerFragmentActivity] and navigating through the given routes.
     */
    fun simulateNavControllerNavigation(
        activityController: ActivityController<NavControllerFragmentActivity> =
            Robolectric.buildActivity(NavControllerFragmentActivity::class.java),
        routes: List<String>,
    ): AppExecutionTimestamps =
        simulateOpeningActivities(
            addStartupActivity = false,
            startInBackground = true,
            activitiesAndActions = listOf(
                activityController to {
                    val navController = activityController.get().getNavController()
                    routes.forEach { route ->
                        clock.tick(POST_ACTIVITY_ACTION_DWELL)
                        navController.navigate(route)
                    }
                },
            )
        )

    fun simulateJvmUncaughtException(exc: Throwable) {
        Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), exc)
    }

    /**
     * Retrieves a data source for use in testing. Generally this function should be discouraged and should only be used when it's
     * non-trivial to invoke the platform API - e.g. when network connectivity changes.
     */
    inline fun <reified T : DataSource> findDataSource(): T {
        val registry = (bootstrapper.instrumentationModule.instrumentationRegistry as FakeInstrumentationRegistry)
        return checkNotNull(registry.findByType(T::class))
    }

    companion object {
        const val LIFECYCLE_EVENT_GAP: Long = 10L
        const val CONNECTIVITY_VALIDATION_GAP: Long = 50L
        const val ACTIVITY_GAP: Long = 100L
        const val STARTUP_BACKGROUND_TIME: Long = 1000L
        const val POST_ACTIVITY_ACTION_DWELL: Long = 10000L
    }
}
