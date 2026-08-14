package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

internal val fullyPopulatedMetadata = EnvelopeMetadata(
    userId = "userId",
    email = "email@example.com",
    username = "username",
    personas = linkedSetOf("persona1", "persona2"),
    timezoneDescription = "Europe/London",
    locale = "en_GB",
)

internal val fullyPopulatedMetadataProto = EnvelopeMetadataProto(
    format_version = FORMAT_VERSION,
    user_id = "userId",
    email = "email@example.com",
    username = "username",
    personas = listOf("persona1", "persona2"),
    timezone_description = "Europe/London",
    locale = "en_GB",
)
