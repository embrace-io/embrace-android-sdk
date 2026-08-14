package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

class PersistenceBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    local: InstrumentedConfig,
    private val remote: RemoteConfig?,
) : PersistenceBehavior {

    private val local = local.enabledFeatures

    override fun isMultiFilePersistenceEnabled(): Boolean =
        thresholdCheck.isBehaviorEnabled(remote?.pctMultiFilePersistenceEnabled)
            ?: local.isMultiFilePersistenceEnabled()
}
