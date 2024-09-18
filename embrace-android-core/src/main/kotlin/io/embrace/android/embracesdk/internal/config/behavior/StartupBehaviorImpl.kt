package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig

/**
 * Provides the behavior that the Startup moment feature should follow.
 */
class StartupBehaviorImpl : StartupBehavior {

    override fun isStartupMomentAutoEndEnabled(): Boolean =
        InstrumentedConfig.enabledFeatures.isStartupMomentAutoEndEnabled()
}
