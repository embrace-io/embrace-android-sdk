package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.clock.Clock

internal class SdkApiImpl(private val clock: Clock) : SdkApi {
    override fun getSdkCurrentTime(): Long = clock.now()
}
