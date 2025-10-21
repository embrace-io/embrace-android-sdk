package io.embrace.android.embracesdk.internal.arch

internal fun String.isBlankish(): Boolean = isBlank() || lowercase() == "null"
