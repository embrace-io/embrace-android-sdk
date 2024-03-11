package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSourceImpl
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeNativeThreadSamplerService
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Test

internal class SessionEnvelopeSourceTest {

    @Test(expected = NotImplementedError::class)
    fun getEnvelope() {
        SessionEnvelopeSource(
            SessionPayloadSourceImpl(
                FakeInternalErrorService(),
                FakeNativeThreadSamplerService()
            )
        ).getEnvelope(SessionSnapshotType.NORMAL_END)
    }
}
