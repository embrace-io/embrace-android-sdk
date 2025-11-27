package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.app.Application
import androidx.annotation.Keep
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @hide
 */
@InternalApi
@Keep
public object AutoSdkInitializationBytecodeEntrypoint {

    @Keep
    @JvmStatic
    public fun onCreate(application: Application) {
        Embrace.start(application)
    }
}
