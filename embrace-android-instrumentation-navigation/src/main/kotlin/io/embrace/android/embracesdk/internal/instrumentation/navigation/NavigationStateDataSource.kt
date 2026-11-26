package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.semconv.EmbFrameCountsAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

/**
 * Reports the screen published against [CurrentScreen.KEY] as navigation state, knowing nothing about where it was resolved.
 *
 * The screen is the one state allowed more than a hundred transitions in a session part: ordinary navigation in a long
 * part can pass a hundred, and the screen is what most telemetry is read against. In return a screen's name is a template,
 * such as a class's name or a route's pattern, and never a URL or a route with its arguments, so that its values stay few.
 *
 * Where state changes are journaled, a transition away from a screen keeps the attributes whose keys are declared here,
 * the two frame counts, and none of what else is collected for the screen.
 */
@OptIn(ExperimentalSemconv::class)
class NavigationStateDataSource(
    private val args: InstrumentationArgs,
    private val activityTracker: AutoCloseable? = null,
) : StateDataSource<Screen>(
    args = args,
    stateTypeFactory = ::NavigationState,
    defaultValue = Screen.Initializing,
    maxTransitions = MAX_NAVIGATION_STATE_TRANSITIONS,
    transitionAttributeKeys = SCREEN_ATTRIBUTE_KEYS,
) {

    override fun onDataCaptureEnabled() {
        super.onDataCaptureEnabled()
        args.eventBus.addStateHandler(CurrentScreen.KEY) { current ->
            onStateChange(newState = current.screen, transitionTimeMs = current.sinceMs)
        }
    }

    override fun onDataCaptureDisabled() {
        activityTracker?.close()
    }

    override fun outgoingValueAttributes(value: Screen): Map<String, String> {
        if (value is SystemStateValue) {
            // no app screen was showing: collect only to discard what accrued, so it isn't attributed to the next screen
            args.navigationTrackingService.collectScreenAttributes { _, _ -> }
            return emptyMap()
        }
        val attributes = mutableMapOf<String, String>()
        args.navigationTrackingService.collectScreenAttributes(attributes::set)
        return attributes
    }

    companion object {
        private const val MAX_NAVIGATION_STATE_TRANSITIONS = 1000

        /**
         * The attributes a journaled transition away from a screen may carry: the frames dropped and expected while it showed.
         */
        private val SCREEN_ATTRIBUTE_KEYS = setOf(
            EmbFrameCountsAttributes.SMOOTHNESS_DROPPED_FRAMES,
            EmbFrameCountsAttributes.SMOOTHNESS_EXPECTED_FRAMES,
        )
    }
}
