package io.embrace.android.embracesdk.internal.comms.api

private val limiters = Endpoint.values().associateWith { EndpointLimiter() }

public val Endpoint.limiter: EndpointLimiter get() = checkNotNull(limiters[this])
