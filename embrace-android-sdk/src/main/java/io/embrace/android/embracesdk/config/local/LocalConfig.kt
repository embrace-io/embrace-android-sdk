package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class LocalConfig(

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    val appId: String,

    /**
     * Control whether the Embrace SDK is able to capture native crashes.
     */
    @SerializedName("ndk_enabled")
    val ndkEnabled: Boolean,

    /**
     * Local config values for the SDK, supplied by the customer.
     */
    val sdkConfig: SdkLocalConfig
)
