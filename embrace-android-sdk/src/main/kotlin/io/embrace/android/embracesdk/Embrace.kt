package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.instance.NoopInternalTelemetryService
import io.embrace.android.embracesdk.internal.instance.NoopSdkInstance
import io.embrace.android.embracesdk.internal.instance.SdkStateHolder
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace

private val sdkStateHolder = try {
    EmbTrace.trace(sectionName = "embrace-impl-init", recordDuration = true) {
        EmbraceImpl()
    }.let { SdkStateHolder(it, it.telemetryService, it.internalErrorHandler) }
} catch (exc: Throwable) {
    // not possible to report, but don't throw
    Log.w("Embrace", "Failed to initialize the Embrace SDK", exc)
    val logger = InternalLoggerImpl()
    SdkStateHolder(NoopSdkInstance(logger, NoopSdkInstance.SDK_NOT_INITIALIZED), NoopInternalTelemetryService, logger)
}

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 *
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressLint("EmbracePublicApiPackageRule")
public object Embrace : SdkApi by sdkStateHolder.dispatcher {

    override fun start(context: Context) {
        try {
            sdkStateHolder.start(context)
        } catch (exc: Throwable) {
            sdkStateHolder.errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
        }
    }

    override fun disable() {
        try {
            sdkStateHolder.disable()
        } catch (exc: Throwable) {
            sdkStateHolder.errorHandler.trackInternalError(InternalErrorType.PublicApiFail, exc)
        }
    }

    /**
     * Gets the singleton instance of the Embrace SDK.
     *
     * @return the instance of the Embrace SDK
     */
    @Deprecated(
        "Calling Embrace.getInstance() is deprecated. Use the Embrace object directly instead. " +
            "For example, Embrace.getInstance().start() is now Embrace.start()",
        replaceWith = ReplaceWith("Embrace"),
    )
    @JvmStatic
    public fun getInstance(): Embrace = this
}
