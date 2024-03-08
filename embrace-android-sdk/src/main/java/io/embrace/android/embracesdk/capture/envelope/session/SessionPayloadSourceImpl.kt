package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.logging.InternalErrorService
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class SessionPayloadSourceImpl(
    private val internalErrorService: InternalErrorService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
) : SessionPayloadSource {

    override fun getSessionPayload(endType: SessionSnapshotType) = SessionPayload(
        spans = null,
        spanSnapshots = null,
        internalError = captureDataSafely { internalErrorService.currentExceptionError?.toNewPayload() },
        sharedLibSymbolMapping = captureDataSafely { nativeThreadSamplerService?.getNativeSymbols() }
    )
}
