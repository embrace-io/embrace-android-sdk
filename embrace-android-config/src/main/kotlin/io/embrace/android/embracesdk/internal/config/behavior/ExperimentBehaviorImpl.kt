package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import kotlin.math.min

class ExperimentBehaviorImpl(
    private val remote: RemoteConfig?,
) : ExperimentBehavior {

    override fun getMaxActiveCount(): Int = min(
        remote?.maxExperimentCount ?: ACTIVE_COUNT_LIMIT,
        MAX_ACTIVE_COUNT_LIMIT,
    )

    override fun getMaxIdLength(): Int = min(
        remote?.maxExperimentIdLength ?: ID_LENGTH_LIMIT,
        MAX_ID_LENGTH_LIMIT,
    )

    override fun getMaxVariantLength(): Int = min(
        remote?.maxExperimentVariantLength ?: VARIANT_LENGTH_LIMIT,
        MAX_VARIANT_LENGTH_LIMIT,
    )

    companion object {
        const val ACTIVE_COUNT_LIMIT: Int = 500
        const val MAX_ACTIVE_COUNT_LIMIT: Int = 5000
        const val ID_LENGTH_LIMIT: Int = 128
        const val MAX_ID_LENGTH_LIMIT: Int = 1024
        const val VARIANT_LENGTH_LIMIT: Int = 128
        const val MAX_VARIANT_LENGTH_LIMIT: Int = 1024
    }
}
