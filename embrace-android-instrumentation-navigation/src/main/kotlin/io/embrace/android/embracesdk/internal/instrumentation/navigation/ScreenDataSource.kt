package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.ScreenState
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks app-driven screen changes reported through the public `Embrace.screenLoaded` API.
 *
 * This datasource is initialized lazily upon first use, i.e. the state span won't be created until the first state update is received.
 */
class ScreenDataSource(
    args: InstrumentationArgs,
) : StateDataSource<String>(
    args = args,
    stateTypeFactory = ::ScreenState,
    defaultValue = INITIAL_VALUE,
    maxTransitions = MAX_SCREEN_STATE_TRANSITIONS,
) {

    override val enableOnCreate: Boolean = false

    private val lastScreen = AtomicReference<String?>(null)

    fun onScreenLoaded(screen: String, attributes: Map<String, String> = emptyMap()) {
        if (lastScreen.getAndSet(screen) != screen) {
            onStateChange(
                newState = screen,
                transitionTimeMs = clock.now(),
                transitionAttributes = attributes,
            )
        }
    }

    companion object {
        private const val INITIAL_VALUE = "Uninitialized"
        private const val MAX_SCREEN_STATE_TRANSITIONS = 1000
    }
}
