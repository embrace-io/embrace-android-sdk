package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeSessionZygote
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FinalEnvelopeParamsTest {
    private val logger = FakeEmbLogger()

    @Test
    fun `verify new session creation of different end types when background activity is enabled`() {
        assertTrue(
            FinalEnvelopeParams(
                initial = fakeSessionZygote(),
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                backgroundActivityEnabled = true,
            ).startNewSession
        )

        assertFalse(
            FinalEnvelopeParams(
                initial = fakeSessionZygote(),
                endType = SessionSnapshotType.PERIODIC_CACHE,
                logger = logger,
                backgroundActivityEnabled = true,
            ).startNewSession
        )

        assertFalse(
            FinalEnvelopeParams(
                initial = fakeSessionZygote(),
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger,
                backgroundActivityEnabled = true,
                crashId = "crashId"
            ).startNewSession
        )
    }

    @Test
    fun `verify new session creation of different end types when background activity is disabled`() {
        assertFalse(
            FinalEnvelopeParams(
                initial = fakeSessionZygote(),
                endType = SessionSnapshotType.NORMAL_END,
                logger = logger,
                backgroundActivityEnabled = false,
            ).startNewSession
        )

        assertFalse(
            FinalEnvelopeParams(
                initial = fakeSessionZygote(),
                endType = SessionSnapshotType.PERIODIC_CACHE,
                logger = logger,
                backgroundActivityEnabled = false,
            ).startNewSession
        )

        assertFalse(
            FinalEnvelopeParams(
                initial = fakeSessionZygote(),
                endType = SessionSnapshotType.JVM_CRASH,
                logger = logger,
                backgroundActivityEnabled = false,
                crashId = "crashId"
            ).startNewSession
        )
    }
}
