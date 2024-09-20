package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.fixtures.testSessionEnvelope
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class EmbraceDeliveryCacheCurrentAccessTest {

    private val serializer = EmbraceSerializer()
    private lateinit var worker: PriorityWorker
    private lateinit var deliveryCacheManager: EmbraceDeliveryCacheManager
    private lateinit var storageService: StorageService
    private lateinit var cacheService: EmbraceCacheService
    private lateinit var logger: EmbLogger
    private lateinit var fakeClock: FakeClock

    @Before
    fun before() {
        fakeClock = FakeClock(clockInit)
        logger = EmbLoggerImpl()
        storageService = FakeStorageService()
        worker = PriorityWorker(SingleThreadTestScheduledExecutor())
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
            logger
        )
    }

    @After
    fun after() {
        clearAllMocks()
    }

    fun `session always replaced after the first write`() {
        val envelope = testSessionEnvelope
        val iterations = 10
        val latch = CountDownLatch(iterations)
        val savesDoneLatch = CountDownLatch(iterations)

        repeat(iterations) {
            SingleThreadTestScheduledExecutor().submit {
                latch.countDown()
                // Running with a snapshot type that will do the save on the current thread
                // Any other type would serially run the writes on the same thread so we can never get into the race condition being tested
                deliveryCacheManager.saveSession(envelope, SessionSnapshotType.JVM_CRASH)
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
