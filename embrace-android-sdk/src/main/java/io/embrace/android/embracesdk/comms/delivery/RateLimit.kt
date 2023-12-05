package io.embrace.android.embracesdk.comms.delivery

internal data class RateLimit(val retries: Int, val retryAfter: Long)
