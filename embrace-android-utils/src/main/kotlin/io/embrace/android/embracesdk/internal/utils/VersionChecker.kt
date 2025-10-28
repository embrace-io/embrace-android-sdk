package io.embrace.android.embracesdk.internal.utils

import androidx.annotation.ChecksSdkIntAtLeast

fun interface VersionChecker {
    @ChecksSdkIntAtLeast(parameter = 0)
    fun isAtLeast(min: Int): Boolean
}
