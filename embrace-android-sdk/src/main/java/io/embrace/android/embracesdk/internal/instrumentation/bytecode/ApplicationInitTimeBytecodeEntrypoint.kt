package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * @hide
 */
@InternalApi
@Keep
public object ApplicationInitTimeBytecodeEntrypoint {

    @Keep
    @JvmStatic
    public fun applicationInitStart() {
        Embrace.applicationInitStart()
    }

    @Keep
    @JvmStatic
    public fun applicationInitEnd() {
        Embrace.applicationInitEnd()
    }
}
