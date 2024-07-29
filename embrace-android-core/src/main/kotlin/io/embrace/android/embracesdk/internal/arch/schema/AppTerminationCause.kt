package io.embrace.android.embracesdk.internal.arch.schema

/**
 * Attribute that stores the reason an app instance terminated
 */
public sealed class AppTerminationCause(
    override val value: String
) : FixedAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey(id = "termination_cause")

    public object Crash : AppTerminationCause("crash")

    public object UserTermination : AppTerminationCause("user_termination")

    public object Unknown : AppTerminationCause("unknown")
}
