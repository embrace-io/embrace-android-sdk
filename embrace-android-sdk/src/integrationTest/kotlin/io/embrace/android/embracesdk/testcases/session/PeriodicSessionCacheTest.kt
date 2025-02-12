package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.findSpanSnapshotOfType
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.getSessionProperty
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.internal.worker.Worker.Background.PeriodicCacheWorker
import io.embrace.android.embracesdk.testframework.FakeCacheStorageService
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asserts that the session is periodically cached.
 */
@RunWith(AndroidJUnit4::class)
internal class PeriodicSessionCacheTest {

    private lateinit var workerThreadModule: FakeWorkerThreadModule
    private lateinit var executor: BlockingScheduledExecutorService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        val clock = FakeClock(SdkIntegrationTestRule.DEFAULT_SDK_START_TIME_MS)
        val fakeInitModule = FakeInitModule(clock = clock)
        executor = BlockingScheduledExecutorService(fakeClock = clock)

        workerThreadModule = FakeWorkerThreadModule(
            fakeInitModule = fakeInitModule,
            testWorkerName = PeriodicCacheWorker,
            priorityWorkerSupplier = { worker ->
                when (worker) {
                    Worker.Priority.DataPersistenceWorker -> PriorityWorker<StoredTelemetryMetadata>(executor)
                    else -> null
                }
            }
        )
        EmbraceSetupInterface(
            overriddenClock = clock,
            overriddenInitModule = fakeInitModule,
            overriddenWorkerThreadModule = workerThreadModule
        )
    }

    @Test
    fun `session is periodically cached`() {
        var snapshot: Envelope<SessionPayload>? = null
        testRule.runTest(
            setupAction = {
                cacheStorageServiceProvider = ::FakeCacheStorageService
            },
            testCaseAction = {
                val cacheStorageService = getCacheStorageService()

                recordSession {
                    assertEquals(0, cacheStorageService.storedPayloads.size)
                    embrace.addSessionProperty("Test", "Test", true)
                    allowPeriodicCacheExecution()
                    assertEquals(1, cacheStorageService.storedPayloads.size)
                    snapshot = loadSnapshot(cacheStorageService)
                }
                allowPeriodicCacheExecution()
            },
            assertAction = {
                val endMessage = checkNotNull(snapshot)
                val span = endMessage.findSpanSnapshotOfType(EmbType.Ux.Session)
                assertNotNull(span.getSessionProperty("Test"))
                span.attributes?.assertMatches(mapOf(
                    "emb.clean_exit" to false,
                    "emb.terminated" to true
                ))
                val completedMessage = getSingleSessionEnvelope()
                val completedSpan = completedMessage.findSessionSpan()
                assertEquals("Test", completedSpan.getSessionProperty("Test"))
                completedSpan.attributes?.assertMatches(mapOf(
                    "emb.clean_exit" to true,
                    "emb.terminated" to false
                ))
            }
        )
    }

    private fun loadSnapshot(cacheStorageService: FakeCacheStorageService): Envelope<SessionPayload> {
        val metadata = cacheStorageService.storedPayloads.keys.single()
        val inputStream = checkNotNull(cacheStorageService.loadPayloadAsStream(metadata))
        return TestPlatformSerializer().fromJson(
            inputStream,
            checkNotNull(SupportedEnvelopeType.SESSION.serializedType)
        )
    }

    private fun allowPeriodicCacheExecution() {
        workerThreadModule.executor.runCurrentlyBlocked()
        executor.runCurrentlyBlocked()
    }

    private fun getCacheStorageService(): FakeCacheStorageService {
        return testRule.bootstrapper.deliveryModule.cacheStorageService as FakeCacheStorageService
    }
}
