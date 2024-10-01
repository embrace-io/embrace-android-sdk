package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.payload.Attribute
import org.junit.Assert.assertEquals

internal fun List<Attribute>.toMap(): Map<String, String> {
    return associateBy { checkNotNull(it.key) }.mapValues { checkNotNull(it.value.data) }
}

/**
 * Asserts that the attributes contain the supplied map of values.
 *
 * This does _not_ check for the presence of any other attributes.
 */
internal fun List<Attribute>.assertMatches(expected: MutableMap<String, Any>.() -> Unit) {
    val expectedMap = mutableMapOf<String, Any>().apply(expected).mapValues { it.toString() }
    val observed = toMap().filterKeys(expectedMap::containsKey)
    assertEquals(expectedMap.toSortedMap(), observed.toSortedMap())
}
