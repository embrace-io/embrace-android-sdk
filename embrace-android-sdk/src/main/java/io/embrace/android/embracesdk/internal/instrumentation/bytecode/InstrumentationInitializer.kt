package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import androidx.annotation.Keep

@Keep
internal class InstrumentationInitializer {
    fun postSdkInit() {
        // Hook that will be invoked after the Embrace SDK successfully initializes.
        // Instrumentation initialization can be injected at this point which ensure the Embrace and OTel SDKs are ready for use.
    }
}
