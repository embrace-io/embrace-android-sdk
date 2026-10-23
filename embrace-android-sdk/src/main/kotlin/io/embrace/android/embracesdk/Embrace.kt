package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.util.Log
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.instance.NoopSdkInstance
import io.embrace.android.embracesdk.internal.instance.createSdkStateHolder
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace

private val sdkApi: SdkApi = try {
    EmbTrace.trace(sectionName = "embrace-impl-init", recordDuration = true) {
        createSdkStateHolder(
            preStartServices = EmbTrace.trace("pre-start-services-init") { PreStartServices() },
        ).api
    }
} catch (exc: Throwable) {
    // not possible to report, but don't throw
    Log.w(
        "Embrace",
        "Failed to create the Embrace SDK instance. All Embrace API calls will be no-ops and no telemetry will be captured.",
        exc,
    )
    // The fallback deliberately touches as few classes as possible, as the failure above may have been class linkage.
    // Anything thrown here escapes as an ExceptionInInitializerError, so no further fallback is attempted.
    val clock = NormalizedIntervalClock()
    NoopSdkInstance(clock, ErrorHandlerImpl(clock), NoopSdkInstance.SDK_NOT_INITIALIZED)
}

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 *
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressLint("EmbracePublicApiPackageRule")
public object Embrace : SdkApi by sdkApi {

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
