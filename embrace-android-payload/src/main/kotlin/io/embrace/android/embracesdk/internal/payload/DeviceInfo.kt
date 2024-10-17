package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class DeviceInfo(

    @Json(name = "dm")
    val manufacturer: String? = null,

    @Json(name = "do")
    val model: String? = null,

    @Json(name = "da")
    val architecture: String? = null,

    @Json(name = "jb")
    val jailbroken: Boolean? = null,

    @Json(name = "lc")
    val locale: String? = null,

    @Json(name = "ms")
    val internalStorageTotalCapacity: Long? = null,

    @Json(name = "os")
    val operatingSystemType: String? = null,

    @Json(name = "ov")
    val operatingSystemVersion: String? = null,

    @Json(name = "oc")
    val operatingSystemVersionCode: Int? = null,

    @Json(name = "sr")
    val screenResolution: String? = null,

    @Json(name = "tz")
    val timezoneDescription: String? = null,

    @Json(name = "nc")
    val cores: Int? = null,

    @Json(name = "pt")
    val cpuName: String? = null,

    @Json(name = "gp")
    val egl: String? = null
)
