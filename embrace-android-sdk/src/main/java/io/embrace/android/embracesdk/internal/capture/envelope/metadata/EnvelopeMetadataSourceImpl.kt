package io.embrace.android.embracesdk.internal.capture.envelope.metadata

import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import java.util.Locale
import java.util.TimeZone

internal class EnvelopeMetadataSourceImpl(
    private val userService: UserService,
) : EnvelopeMetadataSource {

    override fun getEnvelopeMetadata(): EnvelopeMetadata {
        val userInfo = userService.getUserInfo()

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
