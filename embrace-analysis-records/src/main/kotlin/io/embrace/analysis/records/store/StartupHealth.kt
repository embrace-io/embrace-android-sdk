package io.embrace.analysis.records.store

import io.embrace.analysis.perfetto.TraceHealth

/**
 * What a healthy SDK-startup trace looks like, as a [TraceHealth.Profile]: the canary is the slice that
 * wraps `Embrace.start()`, and the class-load burst is measured inside the section that carries the
 * new-user-session write on a production cold start.
 *
 * The threshold: a healthy launch that creates a user session loads up to about fifty classes there
 * (SDK lambdas, schema types, the JSON encoder, app classes the launch happens to touch); the kotlinx
 * builtin serializer table adds roughly seventy more, so the two populations sit well either side of
 * eighty. A launch that restores its user session loads one or two.
 *
 * This lives with the records rather than with the Perfetto client because it is knowledge about the
 * SDK, not about traces; the checker takes it as a parameter and knows nothing of `emb-*` slices.
 */
object StartupHealth {

    const val CANARY: String = "emb-sdk-start"
    const val FIRST_SESSION_SLICE: String = "emb-start-first-session"
    const val CLASS_LOAD_BURST_THRESHOLD: Long = 80L

    val PROFILE: TraceHealth.Profile = TraceHealth.Profile(
        canary = CANARY,
        burstSection = FIRST_SESSION_SLICE,
        burstThreshold = CLASS_LOAD_BURST_THRESHOLD,
        burstAdvice = "a serializer is being resolved at runtime on the new-user-session write (the kotlinx " +
            "builtin serializer table, several times more expensive on an uncompiled install). Every SDK call " +
            "must pass a static SerializationStrategy; look for a reified serializer<T>() that crept back in.",
    )

    /** The startup profile with a different canary: the instrument a reference set or a plan names. */
    fun profileFor(canary: String): TraceHealth.Profile = PROFILE.copy(canary = canary)
}
