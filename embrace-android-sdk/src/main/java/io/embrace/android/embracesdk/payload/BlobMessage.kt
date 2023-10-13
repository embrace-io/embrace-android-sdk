package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.comms.api.ApiClient

internal data class BlobMessage(
    @SerializedName("a")
    val appInfo: AppInfo? = null,

    @SerializedName("bae")
    val applicationExits: List<AppExitInfoData> = emptyList(),

    @SerializedName("d")
    val deviceInfo: DeviceInfo? = null,

    @SerializedName("s")
    val session: BlobSession? = null,

    @SerializedName("u")
    val userInfo: UserInfo? = null,

    @SerializedName("v")
    val version: Int = ApiClient.MESSAGE_VERSION
)
