package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FilesDirApplication
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class ConfigLoadServicesTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `background work only begins once started`() {
        val configService = FakeConfigService()
        val workerThreadPool = FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker))
        val configLoadServices = ConfigLoadServices(
            context = FilesDirApplication(tempDir.newFolder()),
            preStartServices = PreStartServices(errorHandler = FakeErrorHandler(throwOnSdkError = false)),
            workerThreadPoolSupplier = { workerThreadPool },
            configServiceSupplier = { _, _, _, _, _, _ -> configService },
        )
        val ioWorker = workerThreadPool.executorFor(Worker.Background.IoRegWorker)
        assertEquals(0, ioWorker.submitCount)
        assertEquals(0, configService.scheduleConfigRequestsCount)

        configLoadServices.start()
        assertEquals(1, ioWorker.submitCount)
        assertEquals(1, configService.scheduleConfigRequestsCount)
    }
}
