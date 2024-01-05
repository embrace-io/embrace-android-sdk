package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.comms.api.ApiClient

@JsonClass(generateAdapter = true)
internal data class BlobMessage(
    @Json(name = "a")
    val appInfo: AppInfo? = null,

    @Json(name = "bae")
    val applicationExits: List<AppExitInfoData> = emptyList(),

    @Json(name = "d")
    val deviceInfo: DeviceInfo? = null,

    @Json(name = "s")
    val session: BlobSession? = null,

    @Json(name = "u")
    val userInfo: UserInfo? = null,

    @Json(name = "v")
    val version: Int = ApiClient.MESSAGE_VERSION
)
