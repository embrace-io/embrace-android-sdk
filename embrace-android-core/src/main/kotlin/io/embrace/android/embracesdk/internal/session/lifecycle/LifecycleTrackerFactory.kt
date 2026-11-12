package io.embrace.android.embracesdk.internal.session.lifecycle

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Creates the [LifecycleTracker] that detects process state transitions.
 *
 * [AndroidxProcessLifecycleTracker] is the default. [ActivityProcessLifecycleTracker] is only used
 * when enabled by config.
 *
 * @param startupContext the context the SDK was started with. This may be an activity, which is a
 * useful signal when guessing the initial process state, so it must not be the application context.
 */
fun createLifecycleTracker(
    configService: ConfigService,
    eventBus: EventBus,
    startupContext: Context?,
    errorHandler: ErrorHandler,
): LifecycleTracker {
    return when {
        configService.config.autoDataCapture.activityProcessLifecycleTrackerEnabled -> {
            ActivityProcessLifecycleTracker(eventBus, startupContext)
        }
        else -> {
            AndroidxProcessLifecycleTracker(ProcessLifecycleOwner.get(), errorHandler)
        }
    }
}
