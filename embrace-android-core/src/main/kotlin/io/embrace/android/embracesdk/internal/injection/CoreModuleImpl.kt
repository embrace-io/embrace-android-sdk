@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.preference.PreferenceManager
import android.view.WindowManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

class CoreModuleImpl(
    ctx: Context,
    initModule: InitModule,
    versionChecker: VersionChecker = BuildVersionChecker,
) : CoreModule {

    override val context: Context by singleton {
        when (ctx) {
            is Application -> ctx
            else -> ctx.applicationContext
        }
    }

    override val application: Application by singleton { context as Application }

    override val serviceRegistry: ServiceRegistry by singleton {
        ServiceRegistry()
    }

    override val store: KeyValueStore by singleton {
        SharedPrefsStore(
            PreferenceManager.getDefaultSharedPreferences(
                context
            ),
            initModule.jsonSerializer
        )
    }

    override val ordinalStore: OrdinalStore by singleton {
        OrdinalStoreImpl(store)
    }

    override val preferencesService: PreferencesService by singleton {
        EmbracePreferencesService(
            store,
            initModule.clock
        )
    }

    override val activityManager: ActivityManager? by singleton {
        getSystemServiceSafe(Context.ACTIVITY_SERVICE)
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
        runCatching { context.getSystemService(name) }.getOrNull() as T?
}
