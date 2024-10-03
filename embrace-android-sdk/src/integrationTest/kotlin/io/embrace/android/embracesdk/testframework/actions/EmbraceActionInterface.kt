package io.embrace.android.embracesdk.testframework.actions

import android.app.Activity
import android.content.Context
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EmbraceHooks
import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

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

    @Suppress("DEPRECATION")
    fun startSdk(
        context: Context = setup.overriddenCoreModule.context,
        appFramework: Embrace.AppFramework = setup.appFramework,
        configServiceProvider: (framework: AppFramework) -> ConfigService = { setup.overriddenConfigService }
    ) {
        EmbraceHooks.start(context, appFramework, configServiceProvider)
    }

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
        val processStateService = EmbraceHooks.getProcessStateService()
        if (!processStateService.isInBackground) {
            error("Unbalanced call to onForeground() within a test case. Please correct the test.")
        }
        processStateService.onForeground()
    }

    private fun onBackground() {
        val processStateService = EmbraceHooks.getProcessStateService()
        if (processStateService.isInBackground) {
            error("Unbalanced call to onBackground() within a test case. Please correct the test.")
        }
        processStateService.onBackground()
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

    /**
     * Run some [action] and the validate the next saved background activity using
     * [validationFn]. If no background activity is saved with
     * 1 second, this fails.
     */
    internal fun checkNextSavedBackgroundActivity(
        action: () -> Unit,
        validationFn: (Envelope<SessionPayload>) -> Unit
    ): Envelope<SessionPayload> =
        with(bootstrapper.deliveryModule.deliveryService as FakeDeliveryService) {
            checkNextSavedSessionEnvelope(
                dataProvider = ::getSavedBackgroundActivities,
                action = action,
                validationFn = validationFn,
            )
        }

    private fun checkNextSavedSessionEnvelope(
        dataProvider: () -> List<Envelope<SessionPayload>>,
        action: () -> Unit,
        validationFn: (Envelope<SessionPayload>) -> Unit
    ): Envelope<SessionPayload> {
        val startingSize = dataProvider().size
        clock.tick(10_000L)
        action()
        return when (dataProvider().size) {
            startingSize -> {
                returnIfConditionMet(
                    desiredValueSupplier = {
                        dataProvider().getNth(startingSize)
                    },
                    condition = { data ->
                        data.size > startingSize
                    },
                    dataProvider = dataProvider
                )
            }

            else -> {
                dataProvider().getNth(startingSize)
            }
        }.apply {
            validationFn(this)
        }
    }

    private fun <T> List<T>.getNth(n: Int) = filterIndexed { index, _ -> index == n }.single()
}