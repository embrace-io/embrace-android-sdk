package io.embrace.android.embracesdk.internal.utils

fun String.isBlankish(): Boolean = isBlank() || lowercase() == "null"
