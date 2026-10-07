package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import io.embrace.android.embracesdk.EmbraceSdk

/**
 * The real SDK instance, before [EmbraceHolder] has made it reachable from the public API.
 */
internal interface StartableEmbrace : EmbraceSdk {

    /**
     * Starts the SDK. [onStarted] is invoked on the calling thread once the SDK accepts public API calls, before
     * instrumentation is loaded. It is not invoked if the SDK is disabled or fails to initialize.
     */
    fun start(context: Context, onStarted: () -> Unit)

    /**
     * Records that application init started at [timeMs], which may be earlier than this call.
     */
    fun applicationInitStart(timeMs: Long)
}
