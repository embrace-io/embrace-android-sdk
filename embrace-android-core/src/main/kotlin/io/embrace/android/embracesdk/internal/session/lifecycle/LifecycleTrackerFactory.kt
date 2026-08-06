package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Creates the [LifecycleTracker] that detects process state transitions.
 *
 * [AndroidxProcessLifecycleTracker] is the default. [ActivityProcessLifecycleTracker] is only used
 * when enabled by config.
 *
 * @param startupContext the context the SDK was started with. This may be an activity, which is a
 * useful signal when guessing the initial process state, so it must not be the application context.
 */
internal fun createLifecycleTracker(
    configService: ConfigService,
    application: Application,
    startupContext: Context?,
): LifecycleTracker {
    return when {
        configService.autoDataCaptureBehavior.isActivityProcessLifecycleTrackerEnabled() -> {
            ActivityProcessLifecycleTracker(application, startupContext)
        }
        else -> {
            AndroidxProcessLifecycleTracker(ProcessLifecycleOwner.get())
        }
    }
}
