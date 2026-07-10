package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.storage.session.Manifest
import io.embrace.android.embracesdk.internal.delivery.storage.session.PersistedSession
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionMetadata
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartDirectory
import io.embrace.android.embracesdk.internal.delivery.storage.session.SessionPartStore
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span

class FakeSessionPartStore : SessionPartStore {
    override fun isSessionPartActive(sessionPartId: String): Boolean = false
    override fun startSessionPart(session: PersistedSession, manifest: Manifest, metadata: SessionMetadata) = Unit
    override fun appendCompletedSpans(sessionPartId: String, spans: List<Span>) = Unit
    override fun updateSnapshots(sessionPartId: String, snapshots: List<Span>) = Unit
    override fun updateMetadata(sessionPartId: String, metadata: SessionMetadata) = Unit
    override fun onCacheSnapshot(envelope: Envelope<SessionPartPayload>) = Unit
    override fun finalizeSessionPart(envelope: Envelope<SessionPartPayload>): Envelope<SessionPartPayload>? = null
    override fun reconstitute(directory: SessionPartDirectory): Envelope<SessionPartPayload>? = null
    override fun incompleteDirectories(): List<SessionPartDirectory> = emptyList()
    override fun markDelivered(directory: SessionPartDirectory) = Unit
    override fun completedDirectories(): List<SessionPartDirectory> = emptyList()
}
