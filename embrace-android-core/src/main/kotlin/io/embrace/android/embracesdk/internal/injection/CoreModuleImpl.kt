package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl

class CoreModuleImpl(
    ctx: Context,
    initModule: InitModule,
    keyValueStore: Lazy<KeyValueStore>? = null,
) : CoreModule {

    override val context: Context = when (ctx) {
        is Application -> ctx
        else -> ctx.applicationContext
    }

    override val application: Application get() = context as Application

    override val store: KeyValueStore by (keyValueStore ?: lazy { createKeyValueStore(context, initModule.jsonSerializer) })

    override val ordinalStore: OrdinalStore by lazy { OrdinalStoreImpl(store) }
}
