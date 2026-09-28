package io.embrace.android.embracesdk.internal.config.resolved

/**
 * Whether this device falls within a [pct] rollout, or null if [pct] is unset. [bucket] is only read for 0 < pct < 100.
 */
fun rolloutEnabled(pct: Float?, bucket: Lazy<Float>): Boolean? = when {
    pct == null -> null
    pct <= 0 || pct > 100 -> false
    pct == 100f -> true
    else -> pct >= bucket.value
}
