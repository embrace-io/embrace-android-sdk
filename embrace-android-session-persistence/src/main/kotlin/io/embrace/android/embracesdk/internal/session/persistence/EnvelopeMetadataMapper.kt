package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

/**
 * Maps an [EnvelopeMetadata] to its protobuf equivalent.
 */
internal fun EnvelopeMetadata.toProto(resource: MutableResourceProto): EnvelopeMetadataProto = EnvelopeMetadataProto(
    format_version = FORMAT_VERSION,
    user_id = userId,
    email = email,
    username = username,
    personas = personas?.toList().orEmpty(),
    timezone_description = timezoneDescription.orEmpty(),
    locale = locale.orEmpty(),
    resource = resource,
)

internal fun EnvelopeMetadataProto.toPayload(): EnvelopeMetadata = EnvelopeMetadata(
    userId = user_id,
    email = email,
    username = username,
    personas = personas.takeIf(List<String>::isNotEmpty)?.toSet(),
    timezoneDescription = timezone_description.takeIf(String::isNotEmpty),
    locale = locale.takeIf(String::isNotEmpty),
)
