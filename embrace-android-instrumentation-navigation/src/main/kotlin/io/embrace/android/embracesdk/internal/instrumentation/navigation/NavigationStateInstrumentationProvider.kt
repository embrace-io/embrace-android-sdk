package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen

class NavigationStateInstrumentationProvider :
    StateInstrumentationProvider<NavigationStateDataSource, Screen>(
        configGate = {
            configService.config.autoDataCapture.navigationStateCaptureEnabled
        },
    ) {

    override fun factoryProvider(args: InstrumentationArgs): () -> NavigationStateDataSource {
        return {
            val activityTracker = ActivityNavigationTracker(
                clock = args.clock,
                eventBus = args.eventBus,
                navigationTrackingService = args.navigationTrackingService,
                errorHandler = args.errorHandler,
            )
            NavigationStateDataSource(args, activityTracker).also { startPublishing(args, activityTracker) }
        }
    }

    /**
     * Starts the production side of navigation, registering the broker before the Activity source that feeds it so that no
     * signal is emitted with nothing to handle it.
     */
    private fun startPublishing(args: InstrumentationArgs, activityTracker: ActivityNavigationTracker) {
        args.eventBus.addHandler(NavigationSignal.KEY, NavigationEventBroker(args.eventBus))
        args.application.registerActivityLifecycleCallbacks(activityTracker)
        args.processStateTracker.addListener(activityTracker)
    }
}
