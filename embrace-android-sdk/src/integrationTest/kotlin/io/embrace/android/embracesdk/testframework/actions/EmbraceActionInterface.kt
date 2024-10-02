package io.embrace.android.embracesdk.testframework.actions

import android.app.Activity
import android.content.Context
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EmbraceHooks
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.robolectric.Robolectric

/**
 * Interface for performing actions on the [Embrace] instance under test
 */
internal class EmbraceActionInterface(private val setup: EmbraceSetupInterface) {

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
    internal inline fun recordSession(
        simulateActivityCreation: Boolean = false,
        action: EmbraceActionInterface.() -> Unit = {}
    ) {
        // get the activity service & simulate the lifecycle event that triggers a new session.
        val activityService = EmbraceHooks.getActivityService()
        val activityController =
            if (simulateActivityCreation) Robolectric.buildActivity(Activity::class.java) else null

        activityController?.create()
        activityController?.start()
        activityService.onForeground()
        activityController?.resume()

        // perform a custom action during the session boundary, e.g. adding a breadcrumb.
        action()

        // end session 30s later by entering background
        setup.overriddenClock.tick(30000)
        activityController?.pause()
        activityController?.stop()
        activityService.onBackground()
    }
}