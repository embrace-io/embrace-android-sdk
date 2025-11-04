package io.embrace.android.embracesdk.fakes.injection

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.net.ConnectivityManager
import android.view.WindowManager
import io.embrace.android.embracesdk.internal.injection.SystemServiceModule

class FakeSystemServiceModule(
    override val activityManager: ActivityManager? = null,
    override val connectivityManager: ConnectivityManager? = null,
    override val storageManager: StorageStatsManager? = null,
    override val windowManager: WindowManager? = null,
) : SystemServiceModule
