package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class LocalConfig(

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    public val appId: String?,

    /**
     * Control whether the Embrace SDK is able to capture native crashes.
     */
    @Json(name = "ndk_enabled")
    public val ndkEnabled: Boolean,

    /**
     * Local config values for the SDK, supplied by the customer.
     */
    public val sdkConfig: SdkLocalConfig
)
