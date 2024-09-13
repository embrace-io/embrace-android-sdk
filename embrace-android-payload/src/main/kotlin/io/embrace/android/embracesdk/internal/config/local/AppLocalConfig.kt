package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class AppLocalConfig(

    @Json(name = "report_disk_usage") val reportDiskUsage: Boolean? = null
)
