package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen

class NavigationStateInstrumentationProvider :
    StateInstrumentationProvider<NavigationStateDataSource, Screen>(
        configGate = {
            configService.autoDataCaptureBehavior.isNavigationStateCaptureEnabled()
        },
    ) {

    override fun register(args: InstrumentationArgs): DataSourceState<*> {
        val state = super.register(args)
        if (state.dataSource != null) {
            startPublishing(args)
        }
        return state
    }

    override fun factoryProvider(args: InstrumentationArgs): () -> NavigationStateDataSource {
        return { NavigationStateDataSource(args) }
    }

    /**
     * Starts the production side of navigation, registering the broker before the Activity source that feeds it so that no
     * signal is emitted with nothing to handle it.
     */
    private fun startPublishing(args: InstrumentationArgs) {
        args.eventBus.addHandler<NavigationSignal>(NavigationEventBroker(args.eventBus))

        val activityTracker = ActivityNavigationTracker(
            clock = args.clock,
            eventBus = args.eventBus,
            navigationTrackingService = args.navigationTrackingService,
        )
        args.application.registerActivityLifecycleCallbacks(activityTracker)
        args.processStateTracker.addListener(activityTracker)
    }
}
