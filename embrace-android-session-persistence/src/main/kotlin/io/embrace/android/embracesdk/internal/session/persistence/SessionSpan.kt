package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.payload.Span

/**
 * Whether this span represents the session part.
 */
internal fun SpanProto.isSessionSpan(): Boolean = attributes.any {
    it.key == EmbType.Ux.Session.key && it.value_ == EmbType.Ux.Session.value
}

/**
 * Whether this payload span represents the session part.
 */
internal fun Span.isSessionSpan(): Boolean = attributes?.any {
    it.key == EmbType.Ux.Session.key && it.data == EmbType.Ux.Session.value
} == true
