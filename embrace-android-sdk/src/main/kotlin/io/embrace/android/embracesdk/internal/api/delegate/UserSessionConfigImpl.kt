package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.UserSessionListener
import io.embrace.android.embracesdk.config.UserSessionConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

/**
 * Collects the [UserSessionConfig] calls until closed, after which they are dropped. Each call is guarded separately, so
 * one that fails doesn't stop the others from applying.
 */
internal class UserSessionConfigImpl(
    private val errorHandler: ErrorHandler,
) : UserSessionConfig, AutoCloseable {

    private val registered = mutableListOf<UserSessionListener>()

    /**
     * The listeners registered while open, without duplicates, in registration order.
     */
    val listeners: List<UserSessionListener>
        get() = registered

    @Volatile
    private var closed = false

    override fun addListener(listener: UserSessionListener) {
        if (closed) {
            errorHandler.log("SDK config is only applied during Embrace.start(), cannot add_user_session_listener.")
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.PublicApiFail) {
            if (listener !in registered) {
                registered.add(listener)
            }
        }
    }

    override fun close() {
        closed = true
    }
}
