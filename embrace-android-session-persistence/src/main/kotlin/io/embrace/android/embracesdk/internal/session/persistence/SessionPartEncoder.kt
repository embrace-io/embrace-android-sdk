package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * Builds the manifest for a session part.
 */
fun buildSessionManifest(
    resource: EnvelopeResource,
    directory: SessionPartDirectory,
    envelopeVersion: String,
    envelopeType: String,
    sharedLibSymbolMapping: Map<String, String>?,
): SessionManifest = SessionManifest(
    format_version = FORMAT_VERSION,
    envelope_version = envelopeVersion,
    envelope_type = envelopeType,
    user_session_id = directory.userSessionId,
    session_part_id = directory.sessionPartId,
    shared_lib_symbol_mapping = sharedLibSymbolMapping?.let { symbols ->
        SharedLibSymbolMapping(symbols = symbols)
    },
    resource = resource.toImmutableProto(),
)

/**
 * Builds the message holding everything about a session part that can change while it is open.
 */
fun buildEnvelopeMetadata(
    metadata: EnvelopeMetadata,
    resource: EnvelopeResource,
): EnvelopeMetadataProto = metadata.toProto(resource.toMutableProto())

fun buildSessionPartSpan(span: Span): SessionPartSpan = SessionPartSpan(
    format_version = FORMAT_VERSION,
    span = span.toProto(),
)

/**
 * Builds the full set of in-flight spans for a session part.
 */
fun buildSpanSnapshots(spans: List<Span>): SpanSnapshots = SpanSnapshots(
    format_version = FORMAT_VERSION,
    spans = spans.map(Span::toProto),
)

/**
 * Builds one self contained record to append to a session part's span log.
 */
fun buildCompletedSpans(spans: List<Span>): CompletedSpans =
    CompletedSpans(spans = spans.map(Span::toProto))
