package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey

/**
 * Attribute that stores the reason an app instance terminated
 */
sealed class AppTerminationCause(
    override val value: String,
) : EmbraceAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey.create(id = "termination_cause")

    object Crash : AppTerminationCause("crash")

    object UserTermination : AppTerminationCause("user_termination")

    @Suppress("unused")
    object Unknown : AppTerminationCause("unknown")
}
