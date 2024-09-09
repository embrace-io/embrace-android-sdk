package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class LocalConfig(

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    val appId: String?,

    /**
     * Control whether the Embrace SDK is able to capture native crashes.
     */
    @Json(name = "ndk_enabled") val ndkEnabled: Boolean,

    /**
     * Local config values for the SDK, supplied by the customer.
     */
    val sdkConfig: SdkLocalConfig
)
