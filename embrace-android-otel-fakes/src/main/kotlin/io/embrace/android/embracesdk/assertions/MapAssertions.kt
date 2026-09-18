package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.recordedStateValueType
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes.EMB_STATE_VALUE_TYPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull

fun Map<String, String>.findAttributeValue(key: String): String? {
    return get(key)
}

/**
 * Validate that the system value [value] is recorded under [valueKey] along with its value type under [valueTypeKey]. Fails if
 * [value] is not actually a system value.
 */
fun Map<String, String>.assertSystemStateValue(
    value: Any,
    valueKey: String,
    valueTypeKey: String = EMB_STATE_VALUE_TYPE,
) {
    assertEquals(value.toString(), this[valueKey])
    assertEquals(checkNotNull(recordedStateValueType(value)) { "$value is not a system value" }, this[valueTypeKey])
}

/**
 * Validate that the non-system value [value] is recorded under [valueKey] with no value type under [valueTypeKey]. Fails if
 * [value] is actually a system value.
 */
fun Map<String, String>.assertNonSystemStateValue(
    value: Any,
    valueKey: String,
    valueTypeKey: String = EMB_STATE_VALUE_TYPE,
) {
    assertEquals(value.toString(), this[valueKey])
    assertNull("$value is a system value", recordedStateValueType(value))
    assertFalse(containsKey(valueTypeKey))
}

/**
 * Validate that [value] is recorded under [valueKey] with its value type under [valueTypeKey] only if it is a system value, for
 * assertions that are not themselves about the value type.
 */
internal fun Map<String, String>.assertStateValue(
    value: Any,
    valueKey: String,
    valueTypeKey: String = EMB_STATE_VALUE_TYPE,
) {
    assertEquals(value.toString(), this[valueKey])
    assertEquals(recordedStateValueType(value), this[valueTypeKey])
}
