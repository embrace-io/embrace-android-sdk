package io.embrace.android.embracesdk.internal.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object BuildVersionChecker : VersionChecker {
    @ChecksSdkIntAtLeast(parameter = 0)
    override fun isAtLeast(min: Int): Boolean = Build.VERSION.SDK_INT >= min
}
