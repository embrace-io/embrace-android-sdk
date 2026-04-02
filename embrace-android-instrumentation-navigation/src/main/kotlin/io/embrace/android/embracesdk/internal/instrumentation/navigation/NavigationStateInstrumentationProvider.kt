package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen

class NavigationStateInstrumentationProvider :
    StateInstrumentationProvider<NavigationStateDataSource, Screen>(
        configGate = {
            configService.autoDataCaptureBehavior.isNavigationStateCaptureEnabled()
        },
    ) {

    override fun factoryProvider(args: InstrumentationArgs): () -> NavigationStateDataSource {
        return {
            NavigationStateDataSource(
                args = args,
                trackNav = runCatching { Class.forName("androidx.navigation.NavController") }.isSuccess
            )
        }
    }
}
