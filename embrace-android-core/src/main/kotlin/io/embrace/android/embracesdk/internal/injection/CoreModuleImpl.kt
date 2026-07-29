@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import io.embrace.android.embracesdk.internal.prefs.SharedPrefsStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl

class CoreModuleImpl(
    ctx: Context,
    initModule: InitModule,
) : CoreModule {

    override val sdkStartTime: Long = initModule.clock.now()

    override val context: Context = when (ctx) {
        is Application -> ctx
        else -> ctx.applicationContext
    }

    override val application: Application get() = context as Application

    override val store: KeyValueStore = SharedPrefsStore(
        PreferenceManager.getDefaultSharedPreferences(
            context,
        ),
        initModule.jsonSerializer,
    )

    override val ordinalStore: OrdinalStore = OrdinalStoreImpl(store)
}
