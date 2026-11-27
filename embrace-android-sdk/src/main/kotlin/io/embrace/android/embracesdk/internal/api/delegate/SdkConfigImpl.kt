package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.config.OTelConfig
import io.embrace.android.embracesdk.config.SdkConfig
import io.embrace.android.embracesdk.config.UserSessionConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardUserCode
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig

/**
 * Applies the [SdkConfig] block passed to `Embrace.start()`. The block and each section in it are guarded separately: an
 * exception thrown by app code is logged, and the calls made before it still apply. Once [configure] returns, the
 * configuration is closed and later calls on a reference that escaped the block are dropped.
 *
 * OTel config is applied to the [ExternalOtelConfig] as it is made. User session listeners are collected in
 * [userSessionListeners] for the SDK to register once its session orchestrator exists.
 */
internal class SdkConfigImpl(
    otelConfig: ExternalOtelConfig,
    private val errorHandler: ErrorHandler,
) : SdkConfig {

    private val otel = OTelConfigImpl(otelConfig, errorHandler)
    private val userSession = UserSessionConfigImpl(errorHandler)

    @Volatile
    private var closed = false

    val userSessionListeners: List<UserSessionListener>
        get() = userSession.listeners

    fun configure(config: SdkConfig.() -> Unit) {
        try {
            errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
                errorHandler.guardUserCode { config() }
            }
        } finally {
            closed = true
            otel.close()
            userSession.close()
        }
    }

    override fun otel(action: OTelConfig.() -> Unit) = section("otel") { otel.action() }

    override fun userSession(action: UserSessionConfig.() -> Unit) = section("user_session") { userSession.action() }

    private inline fun section(name: String, block: () -> Unit) {
        if (closed) {
            errorHandler.log("SDK config is only applied during Embrace.start(), cannot configure $name.")
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
            errorHandler.guardUserCode(block)
        }
    }
}
