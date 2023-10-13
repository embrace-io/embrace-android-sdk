package io.embrace.android.embracesdk.internal.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

internal fun interface VersionChecker {
    @ChecksSdkIntAtLeast(parameter = 0)
    fun isAtLeast(min: Int): Boolean
}

internal object BuildVersionChecker : VersionChecker {
    @ChecksSdkIntAtLeast(parameter = 0)
    override fun isAtLeast(min: Int) = Build.VERSION.SDK_INT >= min
}
