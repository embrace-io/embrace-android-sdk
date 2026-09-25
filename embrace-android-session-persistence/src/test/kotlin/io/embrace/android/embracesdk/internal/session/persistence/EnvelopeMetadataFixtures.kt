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

internal fun fullyPopulatedMetadataProto(
    envelopeVersion: String = "0.1.0",
    envelopeType: String = "spans",
    userSessionId: String = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    sessionPartId: String = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    sharedLibSymbolMapping: SharedLibSymbolMapping? = null,
): SessionMetadata = SessionMetadata(
    format_version = FORMAT_VERSION,
    envelope_version = envelopeVersion,
    envelope_type = envelopeType,
    user_session_id = userSessionId,
    session_part_id = sessionPartId,
    shared_lib_symbol_mapping = sharedLibSymbolMapping,
    resource = fullyPopulatedResourceProto,
    user_id = "userId",
    email = "email@example.com",
    username = "username",
    personas = listOf("persona1", "persona2"),
    timezone_description = "Europe/London",
    locale = "en_GB",
)
