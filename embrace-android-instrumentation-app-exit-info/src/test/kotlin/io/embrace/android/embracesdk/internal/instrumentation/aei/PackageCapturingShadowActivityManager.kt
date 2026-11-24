package io.embrace.android.embracesdk.internal.instrumentation.aei

import android.app.ActivityManager
import android.os.Build.VERSION_CODES
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivityManager

@Implements(ActivityManager::class)
class PackageCapturingShadowActivityManager : ShadowActivityManager() {

    val queriedPackageNames = mutableListOf<String?>()

    @Implementation(minSdk = VERSION_CODES.R)
    override fun getHistoricalProcessExitReasons(packageName: String?, pid: Int, maxNum: Int): List<*> {
        queriedPackageNames.add(packageName)
        return super.getHistoricalProcessExitReasons(packageName, pid, maxNum)
    }
}
