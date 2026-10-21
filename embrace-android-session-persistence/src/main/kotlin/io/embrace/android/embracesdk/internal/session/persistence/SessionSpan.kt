package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.arch.schema.EmbType

/**
 * Whether this span represents the session part.
 */
internal fun SpanProto.isSessionSpan(): Boolean = attributes.any {
    it.key == EmbType.Ux.Session.key && it.value_ == EmbType.Ux.Session.value
}
