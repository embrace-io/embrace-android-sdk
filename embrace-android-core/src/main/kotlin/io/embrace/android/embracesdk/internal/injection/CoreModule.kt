package io.embrace.android.embracesdk.internal.injection

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore

/**
 * Contains a core set of dependencies that are required by most services/classes in the SDK.
 * This includes a reference to the application context, a clock, logger, etc...
 */
interface CoreModule {

    /**
     * The number of times the SDK has started up for the current app version, including this one.
     * Used as a proxy to determine how many times the app has started for this version.
     */
    val appVersionStartupCounter: Int

    /**
     * Reference to the context. This will always return the application context so won't leak.
     */
    val context: Context

    /**
     * Reference to the current application.
     */
    val application: Application

    val store: KeyValueStore
    val ordinalStore: OrdinalStore
}
