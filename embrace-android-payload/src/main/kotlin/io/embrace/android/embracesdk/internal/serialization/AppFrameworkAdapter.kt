package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.internal.payload.AppFramework

public class AppFrameworkAdapter {
    @ToJson
    public fun toJson(appFramework: AppFramework): Int = appFramework.value

    @FromJson
    public fun fromJson(value: Int): AppFramework? = AppFramework.fromInt(value)
}
