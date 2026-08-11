package io.embrace.android.embracesdk.internal.otel.export

import io.opentelemetry.kotlin.export.OperationResultCode

/**
 * Exports telemetry to the SDK's own in-memory sinks on the calling thread.
 */
interface InlineExporter<T> {

    /**
     * Stores [telemetry] in the SDK's sink, returning once the caller is able to read it back.
     * Never suspends and never blocks on I/O.
     */
    fun exportInline(telemetry: List<T>): OperationResultCode

    /**
     * Waits for any export that [exportInline] handed off to another thread to finish. Shares the
     * signature of the OTel exporters' own `forceFlush` so that a single implementation satisfies
     * both interfaces.
     */
    suspend fun forceFlush(): OperationResultCode
}
