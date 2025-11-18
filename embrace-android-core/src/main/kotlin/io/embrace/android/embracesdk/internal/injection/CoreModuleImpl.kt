@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.EmbracePreferencesService
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import io.embrace.android.embracesdk.internal.registry.ServiceRegistry
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl

class CoreModuleImpl(
    ctx: Context,
    initModule: InitModule,
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
}
