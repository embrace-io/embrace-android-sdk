package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.net.ConnectivityManager
import android.os.PowerManager
import android.view.WindowManager

public interface SystemServiceModule {
    public val activityManager: ActivityManager?
    public val powerManager: PowerManager?
    public val connectivityManager: ConnectivityManager?
    public val storageManager: StorageStatsManager?
    public val windowManager: WindowManager?
}
