package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import org.junit.Assert.assertEquals

fun List<Attribute>.toMap(): Map<String, String> {
    return associateBy { checkNotNull(it.key) }.mapValues { checkNotNull(it.value.data) }
}

fun List<Attribute>.assertMatches(expected: Map<String, Any?>) {
    val expectedMap = expected.toNonNullMap().mapValues { it.value.toString() }.toSortedMap()
    val observedMap = toMap().toNonNullMap().filterKeys(expected::containsKey).toSortedMap()
    assertEquals(expectedMap, observedMap)
}
