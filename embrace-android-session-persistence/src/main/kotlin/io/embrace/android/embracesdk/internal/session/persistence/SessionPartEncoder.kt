package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.EnvelopeMetadata
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * Builds everything a session part is reconstructed from other than its spans: the identity of the
 * part, the envelope resource, and the user info.
 */
fun buildSessionMetadata(
    metadata: EnvelopeMetadata,
    resource: EnvelopeResource,
    directory: SessionPartDirectory,
    envelopeVersion: String,
    envelopeType: String,
    sharedLibSymbolMapping: SharedLibSymbolMapping?,
): SessionMetadata = metadata.toProto(
    directory = directory,
    envelopeVersion = envelopeVersion,
    envelopeType = envelopeType,
    sharedLibSymbolMapping = sharedLibSymbolMapping,
    resource = resource.toProto(),
)

/**
 * Builds the full set of in-flight spans for a session part.
 */
fun buildSpanSnapshots(spans: List<Span>): SpanCollection = SpanCollection(
    format_version = FORMAT_VERSION,
    spans = spans.map(Span::toProto),
)

/**
 * Builds the full log of ended spans for a session part, stamped with the format version.
 */
fun buildCompletedSpans(spans: List<Span>): SpanCollection = SpanCollection(
    spans = spans.map(Span::toProto),
    format_version = FORMAT_VERSION,
)
