package io.embrace.android.embracesdk.internal.session.orchestrator

/**
 * Sentinel object for metadata changes.
 */
internal object MetadataChange

/**
 * A queue of metadata changes that are waiting to be written to disk. Every change has the same
 * identity, so the queue always collapses to one write.
 */
internal fun metadataChangeQueue(): TelemetryQueue<MetadataChange> = TelemetryQueue(identityOf = { it })
