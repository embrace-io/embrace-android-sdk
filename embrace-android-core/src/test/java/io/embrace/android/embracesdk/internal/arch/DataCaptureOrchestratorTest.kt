package io.embrace.android.embracesdk.internal.arch

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class DataCaptureOrchestratorTest {

    private lateinit var orchestrator: DataCaptureOrchestrator
    private lateinit var dataSource: FakeDataSource
    private lateinit var configService: FakeConfigService
    private lateinit var executorService: BlockingScheduledExecutorService
    private var enabled: Boolean = true

    private val syncDataSource = DataSourceState(
        factory = { dataSource },
        configGate = { enabled }
    )

    private val asyncDataSource = DataSourceState(
        factory = { dataSource },
        configGate = { enabled },
        asyncInit = true
    )

    @Before
    fun setUp() {
        dataSource = FakeDataSource(RuntimeEnvironment.getApplication())
        configService = FakeConfigService()
        executorService = BlockingScheduledExecutorService(blockingMode = false)
        orchestrator = DataCaptureOrchestrator(
            BackgroundWorker(executorService),
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `session type change is propagated`() {
        orchestrator.add(syncDataSource)
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, dataSource.enableDataCaptureCount)
    }

    @Test
    fun `async session change`() {
        orchestrator.add(asyncDataSource)
        executorService.blockingMode = true

        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(0, dataSource.enableDataCaptureCount)
        executorService.runCurrentlyBlocked()
        assertEquals(1, dataSource.enableDataCaptureCount)
    }
}
