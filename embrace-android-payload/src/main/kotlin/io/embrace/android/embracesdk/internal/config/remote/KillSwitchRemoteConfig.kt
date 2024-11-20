package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * This contains config values which can turn risky functionality completely off.
 * In normal circumstances these should never actually be used 🤞.
 */
@JsonClass(generateAdapter = true)
data class KillSwitchRemoteConfig(
    @Json(name = "jetpack_compose")
    val jetpackCompose: Boolean? = null,

    @Json(name = "v2_storage")
    val v2StoragePct: Float? = null,
    @Json(name = "use_okhttp")
    val useOkHttpPct: Float? = null,
)
