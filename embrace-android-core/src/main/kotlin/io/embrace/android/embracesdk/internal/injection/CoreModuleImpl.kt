package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.prefs.createKeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.event.EventBus

class CoreModuleImpl(
    ctx: Context,
    initModule: InitModule,
    keyValueStore: Lazy<KeyValueStore>? = null,
) : CoreModule {

    override val context: Context = EmbTrace.trace("context-init") {
        when (ctx) {
            is Application -> ctx
            else -> ctx.applicationContext
        }
    }

    override val application: Application get() = context as Application

    override val store: KeyValueStore by (
        keyValueStore ?: lazy {
            EmbTrace.trace("store-init") { createKeyValueStore(context, initModule.jsonSerializer) }
        }
        )

    override val ordinalStore: OrdinalStore by lazy {
        EmbTrace.trace("ordinal-store-init") { OrdinalStoreImpl(store) }
    }
    override val eventBus: EventBus by lazy {
        EmbTrace.trace("event-bus-init") { EventBus(initModule.errorHandler) }
    }

    override val appVersionStartupCounter: Int by lazy {
        EmbTrace.trace("app-version-startup-counter-init") {
            ordinalStore.incrementAndGet(
                ordinal = Ordinal.APP_VERSION_STARTUP,
                scope = initModule.instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
            )
        }
    }
}
