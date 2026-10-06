package io.embrace.android.embracesdk.internal.config.rollout

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Pct flags whose partial rollouts are reported, keyed by the code sent in payloads.
 */
internal val PCT_ROLLOUT_FLAGS: List<Pair<String, (RemoteConfig) -> Float?>> = listOf(
    "mfp" to { it.pctMultiFilePersistenceEnabled }, // pct_multi_file_persistence_enabled
    "okt" to { it.otelKotlinSdkConfig?.pctEnabled }, // otel_kotlin_sdk.pct_enabled
)
