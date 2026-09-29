package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.SdkModeConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveSdkMode

/**
 * Provides whether the SDK should enable certain 'behavior' modes, such as 'integration mode'
 */
class SdkModeBehaviorImpl(private val config: SdkModeConfig) : SdkModeBehavior {

    constructor(thresholdCheck: BehaviorThresholdCheck, remote: RemoteConfig?) :
        this(resolveSdkMode(behaviorInputs(InstrumentedConfigImpl, remote, thresholdCheck)))

    override fun isSdkDisabled(): Boolean = !config.enabled
}
