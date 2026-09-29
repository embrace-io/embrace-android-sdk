package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.ExperimentConfig
import io.embrace.android.embracesdk.internal.config.resolved.resolveExperiment

class ExperimentBehaviorImpl(private val config: ExperimentConfig) : ExperimentBehavior {

    constructor(remote: RemoteConfig?) : this(resolveExperiment(behaviorInputs(InstrumentedConfigImpl, remote)))

    override fun getMaxExperimentCount(): Int = config.experimentCountLimit
    override fun getMaxIdLength(): Int = config.idLengthLimit
    override fun getMaxVariantLength(): Int = config.variantLengthLimit

    companion object {
        const val MAX_EXPERIMENT_COUNT_LIMIT: Int = ExperimentConfig.EXPERIMENT_COUNT_LIMIT_MAX
    }
}
