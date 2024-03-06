package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fixtures.testSessionMessage
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.storage.StorageService
import io.embrace.android.embracesdk.worker.BackgroundWorker
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryCacheCurrentAccessTest {

    private val serializer = EmbraceSerializer()
    private lateinit var worker: BackgroundWorker
    private lateinit var deliveryCacheManager: EmbraceDeliveryCacheManager
    private lateinit var storageService: StorageService
    private lateinit var cacheService: EmbraceCacheService
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var fakeClock: FakeClock

    @Before
    fun before() {
        fakeClock = FakeClock(clockInit)
        logger = InternalEmbraceLogger()
        storageService = FakeStorageService()
        worker = BackgroundWorker(SingleThreadTestScheduledExecutor())
        cacheService = spyk(
            EmbraceCacheService(
                storageService = storageService,
                serializer = serializer,
                logger = logger
            )
        )
        deliveryCacheManager = EmbraceDeliveryCacheManager(
            cacheService,
            worker,
            logger,
            fakeClock
        )
    }

    @After
    fun after() {
        clearAllMocks()
    }

    fun `session always replaced after the first write`() {
        val sessionMessage = testSessionMessage
        val iterations = 10
        val latch = CountDownLatch(iterations)
        val savesDoneLatch = CountDownLatch(iterations)

        repeat(iterations) {
            SingleThreadTestScheduledExecutor().submit {
                latch.countDown()
                // Running with a snapshot type that will do the save on the current thread
                // Any other type would serially run the writes on the same thread so we can never get into the race condition being tested
                deliveryCacheManager.saveSession(sessionMessage, SessionSnapshotType.JVM_CRASH)
                savesDoneLatch.countDown()
            }
        }
        savesDoneLatch.await(1, TimeUnit.SECONDS)

        verify(exactly = iterations) { cacheService.writeSession(any(), any()) }
    }

    companion object {
        private const val clockInit = 1663800000000
    }
}
