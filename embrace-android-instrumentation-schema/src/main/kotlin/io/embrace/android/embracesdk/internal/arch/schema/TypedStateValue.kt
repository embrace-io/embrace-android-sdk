package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes

/**
 * A value of a [SchemaType.State] whose [toString] representation alone does not identify it uniquely, requiring an optional type
 * to disambiguate state values with the same literal value.
 *
 * Currently, the only function this interface supports is whether they type is a value set by the SDK, which, if true, adds an
 * extra attribute alongside the attribute that holds the literal value so that it can be disambiguated from non-system values
 * with the same literal value.
 *
 * Future extensions of type concept beyond system or non-system values can be isolated to implementors of this interface
 * and the core state code that deal generically with state values through this interface without needing to modify state
 * implementations themselves. This could be in the form of changing this to a sealed class with different explicit types
 * that can be handled generically.
 */
interface TypedStateValue {
    /**
     * True if this value are special cases set by the SDK to denote a non-standard state value.
     */
    val isSystemValue: Boolean
}

/**
 * The value type to record alongside the given state [value], or null if none should be recorded because it is not a system value.
 */
fun recordedStateValueType(value: Any): String? {
    return if ((value as? TypedStateValue)?.isSystemValue == true) {
        EmbStateTransitionAttributes.EmbStateValueTypeValues.SYSTEM
    } else {
        null
    }
}
