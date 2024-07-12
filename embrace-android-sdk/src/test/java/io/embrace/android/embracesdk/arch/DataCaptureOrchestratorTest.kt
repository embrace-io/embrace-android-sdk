package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.SessionType
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class DataCaptureOrchestratorTest {

    private lateinit var orchestrator: DataCaptureOrchestrator
    private lateinit var dataSource: FakeDataSource
    private lateinit var configService: FakeConfigService
    private lateinit var executorService: BlockableExecutorService
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
        dataSource = FakeDataSource(mockContext())
        configService = FakeConfigService()
        executorService = BlockableExecutorService()
        orchestrator = DataCaptureOrchestrator(
            configService,
            BackgroundWorker(executorService),
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `config changes are propagated`() {
        orchestrator.add(syncDataSource)
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, dataSource.enableDataCaptureCount)

        enabled = false
        configService.updateListeners()
        assertEquals(1, dataSource.disableDataCaptureCount)
    }

    @Test
    fun `session type change is propagated`() {
        orchestrator.add(syncDataSource)
        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(1, dataSource.enableDataCaptureCount)
    }

    @Test
    fun `async config change`() {
        orchestrator.add(asyncDataSource)
        executorService.blockingMode = true

        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(0, dataSource.enableDataCaptureCount)
        executorService.runNext()
        assertEquals(1, dataSource.enableDataCaptureCount)

        enabled = false
        configService.updateListeners()
        assertEquals(0, dataSource.disableDataCaptureCount)
        executorService.runNext()
        assertEquals(1, dataSource.disableDataCaptureCount)
    }

    @Test
    fun `async session change`() {
        orchestrator.add(asyncDataSource)
        executorService.blockingMode = true

        assertEquals(0, dataSource.enableDataCaptureCount)
        orchestrator.currentSessionType = SessionType.FOREGROUND
        assertEquals(0, dataSource.enableDataCaptureCount)
        executorService.runNext()
        assertEquals(1, dataSource.enableDataCaptureCount)
    }
}
