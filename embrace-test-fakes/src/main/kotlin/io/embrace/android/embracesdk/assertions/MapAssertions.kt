package io.embrace.android.embracesdk.assertions

fun Map<String, String>.findAttributeValue(key: String): String? {
    return get(key)
}
