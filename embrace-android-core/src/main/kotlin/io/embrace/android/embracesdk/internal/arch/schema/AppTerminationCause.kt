package io.embrace.android.embracesdk.internal.arch.schema

/**
 * Attribute that stores the reason an app instance terminated
 */
sealed class AppTerminationCause(
    override val value: String
) : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "termination_cause")

    object Crash : AppTerminationCause("crash")

    object UserTermination : AppTerminationCause("user_termination")

    object Unknown : AppTerminationCause("unknown")
}
