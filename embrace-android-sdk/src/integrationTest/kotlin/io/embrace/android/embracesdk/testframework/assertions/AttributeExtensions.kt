package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import org.junit.Assert.assertEquals

internal fun List<Attribute>.toMap(): Map<String, String> {
    return associateBy { checkNotNull(it.key) }.mapValues { checkNotNull(it.value.data) }
}

/**
 * Asserts that the attributes contain the supplied map of values.
 *
 * This does _not_ check for the presence of any other attributes.
 */
internal fun List<Attribute>.assertMatches(expected: Map<String, Any?>) {
    val expectedMap = expected.toNonNullMap().mapValues { it.value.toString() }.toSortedMap()
    val observedMap = toMap().toNonNullMap().filterKeys(expected::containsKey).toSortedMap()
    assertEquals(expectedMap, observedMap)
}
