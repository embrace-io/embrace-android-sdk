package io.embrace.android.embracesdk.internal.arch.schema

/**
 * Attribute that stores the reason an app instance terminated
 */
internal sealed class AppTerminationCause(
    override val value: String
) : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "termination_cause")

    internal object Crash : AppTerminationCause("crash")

    internal object UserTermination : AppTerminationCause("user_termination")

    internal object Unknown : AppTerminationCause("unknown")
}
