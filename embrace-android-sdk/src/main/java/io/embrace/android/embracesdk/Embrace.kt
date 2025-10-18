package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.utils.EmbTrace

private val delegate = EmbTrace.trace("embrace-impl-init") { EmbraceImpl() }

/**
 * Entry point for the SDK. This class is part of the Embrace Public API.
 *
 * Contains a singleton instance of itself, and is used for initializing the SDK.
 */
@SuppressLint("EmbracePublicApiPackageRule")
public object Embrace : SdkApi by delegate {

    /**
     * Gets the singleton instance of the Embrace SDK.
     *
     * @return the instance of the Embrace SDK
     */
    @Deprecated(
        "Calling Embrace.getInstance() is deprecated. Use the Embrace object directly instead. " +
            "For example, Embrace.getInstance().start() is now Embrace.start()",
        replaceWith = ReplaceWith("Embrace")
    )
    @JvmStatic
    public fun getInstance(): Embrace = this
}
