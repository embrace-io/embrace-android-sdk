package io.embrace.android.embracesdk.arch.schema

/**
 * Attribute that stores the reason an app instance terminated
 */
internal sealed class AppTerminationCause(
    override val attributeValue: String
) : EmbraceAttribute {
    override val attributeName: String = "termination_cause"

    internal object Crash : AppTerminationCause("crash")

    internal object UserTermination : AppTerminationCause("user_termination")

    internal object Unknown : AppTerminationCause("unknown")
}
