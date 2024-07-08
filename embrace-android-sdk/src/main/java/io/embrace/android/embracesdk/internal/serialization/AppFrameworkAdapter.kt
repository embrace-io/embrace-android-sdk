package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.internal.payload.AppFramework

internal class AppFrameworkAdapter {
    @ToJson
    fun toJson(appFramework: AppFramework) = appFramework.value

    @FromJson
    fun fromJson(value: Int) = AppFramework.fromInt(value)
}
