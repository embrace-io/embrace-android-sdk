package io.embrace.android.embracesdk.testcases.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fixtures.FAKE_SESSION_PART_ID
import io.embrace.android.embracesdk.fixtures.FAKE_USER_SESSION_ID
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A session part persisted by the multi file layer must reach the intake service before payload
 * resurrection starts.
 */
@RunWith(AndroidJUnit4::class)
internal class MultiFilePersistenceResurrectionOrderTest {

    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.IoRegWorker,
            ),
            fakeStorageLayer = true,
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }.also {
            cacheStorageService = checkNotNull(it.fakeCacheStorageService)
        }
    }

    @Test
    fun `session part resurrection`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(pctMultiFilePersistenceEnabled = 100.0f),
            setupAction = {
                persistSessionPart(
                    userSessionId = FAKE_USER_SESSION_ID,
                    sessionPartId = FAKE_SESSION_PART_ID,
                )
                cacheStorageService.addPayload(
                    fakeCachedSessionStoredTelemetryMetadata,
                    fakeIncompleteSessionEnvelope(
                        userSessionId = FAKE_USER_SESSION_ID,
                        sessionPartId = FAKE_SESSION_PART_ID,
                    ),
                )
            },
            testCaseAction = {},
            assertAction = {
                val envelope = getSessionEnvelopes(1, assertOrdering = false).single()
                assertEquals(FAKE_SESSION_PART_ID, envelope.getSessionPartId())
                assertEquals(
                    "the redundant cached copy was not discarded",
                    emptyList<StoredTelemetryMetadata>(),
                    cacheStorageService.getUndeliveredPayloads(),
                )
            },
        )
    }
}
