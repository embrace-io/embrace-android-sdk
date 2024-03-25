package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource

internal class AppFrameworkAdapter {
    @ToJson
    fun toJson(appFramework: EnvelopeResource.AppFramework) = appFramework.value

    @FromJson
    fun fromJson(value: Int) = EnvelopeResource.AppFramework.fromInt(value)
}
