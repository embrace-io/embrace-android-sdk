package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

/**
 * Represents the startup moment configuration element specified in the Embrace config file.
 */
internal class StartupMomentLocalConfig(

    @SerializedName("automatically_end")
    val automaticallyEnd: Boolean? = null
)
