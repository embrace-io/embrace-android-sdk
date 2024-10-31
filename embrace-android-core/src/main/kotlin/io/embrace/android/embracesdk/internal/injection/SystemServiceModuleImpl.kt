package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

internal class SystemServiceModuleImpl @JvmOverloads constructor(
    coreModule: CoreModule,
    versionChecker: VersionChecker = BuildVersionChecker,
) : SystemServiceModule {

    private val ctx = coreModule.context

    override val activityManager: ActivityManager? by singleton {
        getSystemServiceSafe(Context.ACTIVITY_SERVICE)
    }

    override val powerManager: PowerManager? by singleton {
        getSystemServiceSafe(Context.POWER_SERVICE)
    }

    override val connectivityManager: ConnectivityManager? by singleton {
        getSystemServiceSafe(Context.CONNECTIVITY_SERVICE)
    }

    override val storageManager: StorageStatsManager? by singleton {
        if (versionChecker.isAtLeast(Build.VERSION_CODES.O)) {
            getSystemServiceSafe(Context.STORAGE_STATS_SERVICE) as StorageStatsManager?
        } else {
            null
        }
    }

    override val windowManager: WindowManager? by singleton {
        getSystemServiceSafe(Context.WINDOW_SERVICE)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getSystemServiceSafe(name: String): T? =
        runCatching { ctx.getSystemService(name) }.getOrNull() as T?
}
