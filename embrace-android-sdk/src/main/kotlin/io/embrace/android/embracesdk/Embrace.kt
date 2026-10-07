package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.content.Context
import io.embrace.android.embracesdk.internal.instance.EmbraceHolder
import io.embrace.android.embracesdk.internal.utils.EmbTrace

private val holder = EmbraceHolder(
    factory = { EmbTrace.trace(sectionName = "embrace-impl-init", recordDuration = true) { EmbraceImpl() } },
)

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 *
 * Call [start] once, as early as possible in `Application.onCreate()`. Every function on this object forwards to
 * the current SDK instance, which buffers or drops calls before start and is a no-op if the SDK is disabled.
 *
 * Nothing on this object throws, whether or not the SDK has started.
 */
@SuppressLint("EmbracePublicApiPackageRule")
public object Embrace : EmbraceSdk by holder {

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     *
     * See [Embrace Docs](https://embrace.io/docs/android/) for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context an instance of the application context
     */
    public fun start(context: Context) {
        holder.start(context)
    }
}
