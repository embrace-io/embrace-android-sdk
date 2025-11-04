package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.net.ConnectivityManager
import android.view.WindowManager

interface SystemServiceModule {
    val activityManager: ActivityManager?
    val connectivityManager: ConnectivityManager?
    val storageManager: StorageStatsManager?
    val windowManager: WindowManager?
}
