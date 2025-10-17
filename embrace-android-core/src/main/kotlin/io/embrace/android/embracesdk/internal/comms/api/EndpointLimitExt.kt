package io.embrace.android.embracesdk.internal.comms.api

private val limiters = Endpoint.entries.associateWith { EndpointLimiter() }

internal val Endpoint.limiter: EndpointLimiter get() = checkNotNull(limiters[this])
