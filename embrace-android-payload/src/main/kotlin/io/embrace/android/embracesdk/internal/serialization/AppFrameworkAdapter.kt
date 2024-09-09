package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.internal.payload.AppFramework

class AppFrameworkAdapter {
    @ToJson
    fun toJson(appFramework: AppFramework): Int = appFramework.value

    @FromJson
    fun fromJson(value: Int): AppFramework? = AppFramework.fromInt(value)
}
