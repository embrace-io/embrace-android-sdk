package io.embrace.android.embracesdk.internal.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

fun interface VersionChecker {
    @ChecksSdkIntAtLeast(parameter = 0)
    fun isAtLeast(min: Int): Boolean
}

object BuildVersionChecker : VersionChecker {
    @ChecksSdkIntAtLeast(parameter = 0)
    override fun isAtLeast(min: Int): Boolean = Build.VERSION.SDK_INT >= min
}
