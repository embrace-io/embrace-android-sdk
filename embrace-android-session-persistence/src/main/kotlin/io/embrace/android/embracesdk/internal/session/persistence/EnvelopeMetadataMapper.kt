package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata

/**
 * Maps an [EnvelopeMetadata] to its protobuf equivalent, alongside the rest of the data a session
 * part is reconstructed from.
 */
internal fun EnvelopeMetadata.toProto(
    directory: SessionPartDirectory,
    envelopeVersion: String,
    envelopeType: String,
    sharedLibSymbolMapping: SharedLibSymbolMapping?,
    resource: ResourceProto,
): SessionMetadata = SessionMetadata(
    format_version = FORMAT_VERSION,
    envelope_version = envelopeVersion,
    envelope_type = envelopeType,
    user_session_id = directory.userSessionId,
    session_part_id = directory.sessionPartId,
    shared_lib_symbol_mapping = sharedLibSymbolMapping,
    resource = resource,
    user_id = userId,
    email = email,
    username = username,
    personas = personas?.toList().orEmpty(),
    timezone_description = timezoneDescription.orEmpty(),
    locale = locale.orEmpty(),
)

internal fun SessionMetadata.toPayload(): EnvelopeMetadata = EnvelopeMetadata(
    userId = user_id,
    email = email,
    username = username,
    personas = personas.takeIf(List<String>::isNotEmpty)?.toSet(),
    timezoneDescription = timezone_description.takeIf(String::isNotEmpty),
    locale = locale.takeIf(String::isNotEmpty),
)
