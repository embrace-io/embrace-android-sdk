package io.embrace.android.embracesdk.internal.envelope.metadata

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.UserInfo
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.Locale
import java.util.TimeZone

internal class EnvelopeMetadataSourceImpl(
    private val userInfoProvider: Provider<UserInfo>,
) : EnvelopeMetadataSource {

    override fun getEnvelopeMetadata(): EnvelopeMetadata {
        val userInfo = userInfoProvider()

        return EnvelopeMetadata(
            userId = userInfo.userId,
            email = userInfo.email,
            username = userInfo.username,
            personas = userInfo.personas ?: emptySet(),
            timezoneDescription = TimeZone.getDefault().id,
            locale = Locale.getDefault().language + "_" + Locale.getDefault().country
        )
    }
}
