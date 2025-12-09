package io.embrace.android.embracesdk.internal.injection

import android.content.Context

@Suppress("UNCHECKED_CAST")
internal fun <T> Context.getSystemServiceSafe(name: String): T? =
    runCatching { getSystemService(name) }.getOrNull() as T?
