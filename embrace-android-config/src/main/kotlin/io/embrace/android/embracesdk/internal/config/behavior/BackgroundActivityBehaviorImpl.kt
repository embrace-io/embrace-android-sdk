package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.BackgroundActivityConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveBackgroundActivity

/**
 * Provides the behavior that the Background Activity feature should follow.
 */
class BackgroundActivityBehaviorImpl(private val config: BackgroundActivityConfig) : BackgroundActivityBehavior {

    constructor(thresholdCheck: BehaviorThresholdCheck, local: InstrumentedConfig, remote: RemoteConfig?) :
        this(resolveBackgroundActivity(behaviorInputs(local, remote, thresholdCheck)))

    override fun isBackgroundActivityCaptureEnabled(): Boolean = config.captureEnabled
    override fun getManualBackgroundActivityLimit(): Int = config.manualBackgroundActivityLimit
    override fun getMinBackgroundActivityDuration(): Long = config.minBackgroundActivityDuration
    override fun getMaxCachedActivities(): Int = config.maxCachedActivities
}
