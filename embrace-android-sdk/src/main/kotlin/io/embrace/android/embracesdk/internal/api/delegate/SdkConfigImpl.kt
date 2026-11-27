package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.config.OTelConfig
import io.embrace.android.embracesdk.config.SdkConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardUserCode
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig

/**
 * Applies the [SdkConfig] block passed to `Embrace.start()`. The block and each section in it are guarded separately: an
 * exception thrown by app code is logged, and the calls made before it still apply. Once [configure] returns, the
 * configuration is closed and later calls on a reference that escaped the block are dropped.
 */
internal class SdkConfigImpl(
    otelConfig: ExternalOtelConfig,
    private val errorHandler: ErrorHandler,
) : SdkConfig {

    private val otel = OTelConfigImpl(otelConfig, errorHandler)

    @Volatile
    private var closed = false

    fun configure(config: SdkConfig.() -> Unit) {
        try {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
                errorHandler.guardUserCode { config() }
            }
        } finally {
            closed = true
            otel.close()
        }
    }

    override fun otel(action: OTelConfig.() -> Unit) {
        if (closed) {
            errorHandler.log("SDK config is only applied during Embrace.start(), cannot configure otel.")
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
            errorHandler.guardUserCode { otel.action() }
        }
    }
}
