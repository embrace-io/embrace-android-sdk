package io.embrace.android.embracesdk.testcases

import io.embrace.android.embracesdk.internal.payload.Attribute

internal fun List<Attribute>.findAttrValue(key: String) = singleOrNull { attr -> attr.key == key }?.data

internal fun List<Attribute>.toMap(): Map<String, String> = associateBy { checkNotNull(it.key) }.mapValues { checkNotNull(it.value.data) }
