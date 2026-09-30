package io.embrace.android.embracesdk.internal.config.resolved

import kotlin.math.min

/**
 * Resolved experiment tracking config. Values are capped at their MAX_* ceiling.
 */
interface ExperimentConfig {
    val maxCount: Int
    val maxIdLength: Int
    val maxVariantLength: Int

    companion object {
        const val DEFAULT_MAX_COUNT: Int = 500
        const val MAX_COUNT_LIMIT: Int = 5000
        const val DEFAULT_MAX_ID_LENGTH: Int = 128
        const val MAX_ID_LENGTH_LIMIT: Int = 1024
        const val DEFAULT_MAX_VARIANT_LENGTH: Int = 128
        const val MAX_VARIANT_LENGTH_LIMIT: Int = 1024
    }
}

/**
 * Creates an [ExperimentConfig]. A provider returning null means the default is used.
 */
inline fun ExperimentConfig(
    crossinline maxCount: () -> Int? = { null },
    crossinline maxIdLength: () -> Int? = { null },
    crossinline maxVariantLength: () -> Int? = { null },
): ExperimentConfig = object : ExperimentConfig {
    override val maxCount: Int = min(maxCount() ?: ExperimentConfig.DEFAULT_MAX_COUNT, ExperimentConfig.MAX_COUNT_LIMIT)
    override val maxIdLength: Int =
        min(maxIdLength() ?: ExperimentConfig.DEFAULT_MAX_ID_LENGTH, ExperimentConfig.MAX_ID_LENGTH_LIMIT)
    override val maxVariantLength: Int =
        min(maxVariantLength() ?: ExperimentConfig.DEFAULT_MAX_VARIANT_LENGTH, ExperimentConfig.MAX_VARIANT_LENGTH_LIMIT)
}
