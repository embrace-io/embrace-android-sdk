package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents the base URLs element specified in the Embrace config file.
 */
internal class BaseUrlLocalConfig(
    @SerializedName("config")
    val config: String? = null,

    @SerializedName("data")
    val data: String? = null,

    @SerializedName("data_dev")
    val dataDev: String? = null,

    @SerializedName("images")
    val images: String? = null
)
