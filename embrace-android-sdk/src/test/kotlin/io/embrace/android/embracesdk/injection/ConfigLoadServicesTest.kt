package io.embrace.android.embracesdk.injection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ConfigLoadServicesTest {

    @Test
    fun `background work only begins once started`() {
        val configService = FakeConfigService()
        val workerThreadPool = FakeWorkerThreadPool(testWorkers = listOf(Worker.Background.IoRegWorker))
        val configLoadServices = ConfigLoadServices(
            context = ApplicationProvider.getApplicationContext(),
            preStartServices = PreStartServices(logger = FakeInternalLogger(throwOnInternalError = false)),
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
