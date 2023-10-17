package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.InternalApi

/**
 * A set of methods exposed internally for other Embrace SDK modules to use - not supported publicly and can change at any time.
 */
@InternalApi
public interface SdkApi {
    public fun getSdkCurrentTime(): Long
}

/**
 * Default implementation used when instance bound to the SDK cannot be used
 */
internal val default: SdkApi = object : SdkApi {
    override fun getSdkCurrentTime(): Long = System.currentTimeMillis()
}
