package io.embrace.android.embracesdk.testframework.actions

import android.app.Activity
import androidx.lifecycle.Lifecycle
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.robolectric.Robolectric

/**
 * Interface for performing actions on the [Embrace] instance under test
 */
internal class EmbraceActionInterface(
    private val setup: EmbraceSetupInterface,
    private val bootstrapper: ModuleInitBootstrapper
) {

    /**
     * The [Embrace] instance that can be used for testing
     */
    val embrace = Embrace.getInstance()

    val clock: FakeClock
        get() = setup.overriddenClock

    val configService: FakeConfigService
        get() = setup.overriddenConfigService

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
    internal fun recordSession(action: EmbraceActionInterface.() -> Unit = {}) {
        onForeground()

        // perform a custom action during the session boundary, e.g. adding a breadcrumb.
        action()

        // end session 30s later by entering background
        setup.overriddenClock.tick(30000)
        onBackground()
    }

    private fun onForeground() {
        setup.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun onBackground() {
        setup.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun simulateNetworkChange(status: NetworkStatus) {
        val service = (bootstrapper.essentialServiceModule.networkConnectivityService as FakeNetworkConnectivityService)
        service.networkStatus = status
    }

    fun simulateActivityLifecycle() {
        with(Robolectric.buildActivity(Activity::class.java)) {
            create()
            start()
            resume()
            clock.tick(30000)
            pause()
            stop()
            destroy()
        }
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
}
