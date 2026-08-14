package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

/**
 * Maps an [EnvelopeMetadata] to its protobuf equivalent.
 */
internal fun EnvelopeMetadata.toProto(): EnvelopeMetadataProto = EnvelopeMetadataProto(
    user_id = userId,
    email = email,
    username = username,
    personas = personas?.toList().orEmpty(),
    timezone_description = timezoneDescription.orEmpty(),
    locale = locale.orEmpty(),
)
