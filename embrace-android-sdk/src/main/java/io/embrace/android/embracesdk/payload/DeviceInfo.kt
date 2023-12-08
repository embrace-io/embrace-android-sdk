package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

internal data class DeviceInfo(

    @SerializedName("dm")
    val manufacturer: String? = null,

    @SerializedName("do")
    val model: String? = null,

    @SerializedName("da")
    val architecture: String? = null,

    @SerializedName("jb")
    val jailbroken: Boolean? = null,

    @SerializedName("lc")
    val locale: String? = null,

    @SerializedName("ms")
    val internalStorageTotalCapacity: Long? = null,

    @SerializedName("os")
    val operatingSystemType: String? = null,

    @SerializedName("ov")
    val operatingSystemVersion: String? = null,

    @SerializedName("oc")
    val operatingSystemVersionCode: Int? = null,

    @SerializedName("sr")
    val screenResolution: String? = null,

    @SerializedName("tz")
    val timezoneDescription: String? = null,

    @SerializedName("up")
    val uptime: Long? = null,

    @SerializedName("nc")
    val cores: Int? = null,

    @SerializedName("pt")
    val cpuName: String? = null,

    @SerializedName("gp")
    val egl: String? = null
)
