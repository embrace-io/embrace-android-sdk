package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class AppLocalConfig(

    @SerializedName("report_disk_usage")
    val reportDiskUsage: Boolean? = null
)
