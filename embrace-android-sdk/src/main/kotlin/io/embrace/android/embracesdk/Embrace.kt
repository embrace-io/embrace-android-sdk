package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.instance.BufferingSdkInstance
import io.embrace.android.embracesdk.internal.instance.NoopInternalTelemetryService
import io.embrace.android.embracesdk.internal.instance.NoopSdkInstance
import io.embrace.android.embracesdk.internal.instance.SdkStateHolder
import io.embrace.android.embracesdk.internal.instance.createSdkStateHolder
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace

private val sdkStateHolder = try {
    EmbTrace.trace(sectionName = "embrace-impl-init", recordDuration = true) {
        createSdkStateHolder()
    }
} catch (exc: Throwable) {
    // not possible to report, but don't throw
    Log.w(
        "Embrace",
        "Failed to create the Embrace SDK instance. All Embrace API calls will be no-ops and no telemetry will be captured.",
        exc,
    )
    val logger = InternalLoggerImpl()
    val instance = NoopSdkInstance(logger, NoopSdkInstance.SDK_NOT_INITIALIZED)
    SdkStateHolder(
        instanceFactory = { instance },
        preStart = { BufferingSdkInstance(System::currentTimeMillis, logger) },
        failed = { instance },
        disabled = { instance },
        telemetryService = NoopInternalTelemetryService,
        errorHandler = logger,
    )
}

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 *
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressLint("EmbracePublicApiPackageRule")
public object Embrace : SdkApi by sdkStateHolder.api {

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
